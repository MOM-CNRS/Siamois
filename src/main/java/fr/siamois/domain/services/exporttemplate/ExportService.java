package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Point d'entrée de l'export d'un projet avec un modèle : vérifie les droits de l'utilisateur (accès en
 * lecture au projet, qui doit appartenir à son institution) et la cohérence du modèle avec les registres
 * du code, puis confie la production du classeur à {@link ExportEngine}.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportTemplateService exportTemplateService;
    private final ProfilePermissionService profilePermissionService;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonProfileAssignmentRepository assignmentRepository;
    private final ExportEngine exportEngine;

    /** Un projet proposé à l'export : identifiant technique, identifiant complet (peut être vide) et nom. */
    public record ProjectChoice(Long id, String fullIdentifier, String name) {
    }

    /**
     * Les projets de l'institution de l'utilisateur qu'il peut exporter : tous s'il voit les données de
     * l'institution, sinon ceux sur lesquels il a un profil. Triés par identifiant complet.
     */
    @Transactional(readOnly = true)
    public List<ProjectChoice> listReadableProjects(UserInfo userInfo) {
        if (userInfo.getInstitution() == null) {
            return List.of();
        }
        Long institutionId = userInfo.getInstitution().getId();
        Set<Long> allowed = profilePermissionService.canViewInstitutionData(userInfo.getUser(), userInfo.getInstitution())
                ? null
                : assignmentRepository.findInstitutionActionUnitIdsWithAnyProfile(userInfo.getUser().getId(), institutionId);
        return actionUnitRepository.findSummariesByCreatedByInstitutionId(institutionId).stream()
                .filter(p -> allowed == null || allowed.contains(p.getId()))
                .map(p -> new ProjectChoice(p.getId(), p.getFullIdentifier(), p.getName()))
                .sorted(Comparator.comparing(p -> p.fullIdentifier() == null ? "" : p.fullIdentifier()))
                .toList();
    }

    /**
     * @throws NoSuchElementException          si le projet ou le modèle n'existe pas
     * @throws ForbiddenOperationException     si le projet n'est pas lisible par l'utilisateur
     * @throws InvalidExportTemplateException  si le modèle ne correspond pas aux registres du code
     */
    @Transactional(readOnly = true)
    public ExportEngine.Result export(UserInfo userInfo, Long templateId, Long projectId) {
        assertCanReadProject(userInfo, projectId);
        ExportTemplateDefinition definition = exportTemplateService.getDefinition(userInfo, templateId);
        assertMatchesRegistries(definition);
        return exportEngine.run(definition, projectId, userInfo.getLang());
    }

    /**
     * Aperçu d'une définition (éventuellement non enregistrée, celle de l'éditeur) sur un projet : mêmes
     * droits et mêmes contrôles que l'export, mais seulement {@code maxRows} lignes par source.
     */
    @Transactional(readOnly = true)
    public ExportEngine.Preview preview(UserInfo userInfo, ExportTemplateDefinition definition, Long projectId, int maxRows) {
        assertCanReadProject(userInfo, projectId);
        assertMatchesRegistries(definition);
        return exportEngine.preview(definition, projectId, userInfo.getLang(), maxRows);
    }

    private void assertCanReadProject(UserInfo userInfo, Long projectId) {
        if (!actionUnitRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project " + projectId + " not found");
        }
        boolean sameInstitution = userInfo.getInstitution() != null
                && actionUnitRepository.existsByIdAndCreatedByInstitutionId(projectId, userInfo.getInstitution().getId());
        if (!sameInstitution || !profilePermissionService.canViewProject(userInfo.getUser(), userInfo.getInstitution(), projectId)) {
            throw new ForbiddenOperationException("Cannot export project " + projectId);
        }
    }

    private static void assertMatchesRegistries(ExportTemplateDefinition definition) {
        List<String> problems = ExportTemplateChecker.check(definition);
        if (!problems.isEmpty()) {
            throw new InvalidExportTemplateException("Template does not match this application: " + String.join("; ", problems));
        }
    }
}
