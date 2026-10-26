package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The common start of creating an entity with nothing but a project and a type (recording unit, phase,
 * container, study, document — {@code POST /api/v1/<entités>}), and of the identifier suggestion its
 * creation form shows ({@code GET /api/v1/identifier-suggestions}): the project in the caller's scope, its
 * organization, the caller's context there, the type, and the new entity built from them.
 */
@Service
@RequiredArgsConstructor
public class ProjectTypedCreationService {

    private final ActionUnitService actionUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;

    /**
     * What differs between the entities created in a project: the message of an unknown type, and how
     * their DTO takes its project and its type.
     */
    public record Kind<D extends AbstractEntityDTO>(String typeNotFoundMessage, Supplier<D> factory,
                                                     BiConsumer<D, ActionUnitSummaryDTO> projectSetter,
                                                     BiConsumer<D, ConceptDTO> typeSetter) {
        public static final Kind<RecordingUnitDTO> RECORDING_UNIT = new Kind<>("Type d'UE introuvable",
                RecordingUnitDTO::new, RecordingUnitDTO::setActionUnit, RecordingUnitDTO::setType);
        public static final Kind<PhaseDTO> PHASE = new Kind<>("Type de phase introuvable",
                PhaseDTO::new, PhaseDTO::setActionUnit, PhaseDTO::setType);
        public static final Kind<ContainerDTO> CONTAINER = new Kind<>("Type de contenant introuvable",
                ContainerDTO::new, ContainerDTO::setActionUnit, ContainerDTO::setType);
        public static final Kind<StudyDTO> STUDY = new Kind<>("Type d'étude introuvable",
                StudyDTO::new, StudyDTO::setActionUnit, StudyDTO::setType);
        public static final Kind<DocumentDTO> DOCUMENT = new Kind<>("Catégorie de document introuvable",
                DocumentDTO::new, DocumentDTO::setActionUnit, DocumentDTO::setType);
    }

    /** The project a creation targets, and the caller's context in it (its organization, their language). */
    public record ProjectContext(ActionUnitDTO project, InstitutionDTO institution, UserInfo userInfo) {
        public ActionUnitSummaryDTO projectSummary() {
            return summaryOf(project);
        }
    }

    /**
     * The whole summary of a project — {@code new ActionUnitSummaryDTO(project)} keeps only its id and name,
     * while the identifier formats read its own identifiers ({@code ID_UA}).
     */
    public static ActionUnitSummaryDTO summaryOf(ActionUnitDTO project) {
        ActionUnitSummaryDTO summary = new ActionUnitSummaryDTO(project);
        summary.setFullIdentifier(project.getFullIdentifier());
        summary.setIdentifier(project.getIdentifier());
        summary.setType(project.getType());
        summary.setBeginDate(project.getBeginDate());
        summary.setEndDate(project.getEndDate());
        return summary;
    }

    /** 400 when {@code projectId} is blank or the project has no organization, 404 when out of scope. */
    public ProjectContext resolveProject(String projectId, PersonDTO personDto,
                                         Set<Long> accessibleInstitutionIds, String lang) {
        ActionUnitDTO project = actionUnitService.findAccessibleProjectByKey(
                OpenApiParamIds.requireNonBlank(projectId, "projectId"), accessibleInstitutionIds).actionUnit();
        InstitutionDTO institution = project.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        return new ProjectContext(project, institution, new UserInfo(institution, personDto, lang));
    }

    /** 403 ({@code forbiddenMessage}) unless the caller holds one of the permission's three scopes on the project. */
    public void requirePermission(ProjectContext context, String forbiddenMessage,
                                  String instanceCode, String organizationCode, String projectCode) {
        if (!profilePermissionService.hasProjectPermission(context.userInfo(), context.project().getId(),
                instanceCode, organizationCode, projectCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }

    /** 400 when {@code typeId} is blank or not numeric, 404 when no concept has it. */
    public ConceptDTO resolveType(String typeId, Kind<?> kind) {
        long conceptId = OpenApiParamIds.parseRequiredConceptId(typeId, "typeId");
        return conceptService.findById(conceptId)
                .map(conceptMapper::convert)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, kind.typeNotFoundMessage()));
    }

    /** The new, unsaved entity: in the project, of the type, created by the caller in the project's organization. */
    public <D extends AbstractEntityDTO> D newEntity(Kind<D> kind, ProjectContext context, ConceptDTO type) {
        D entity = kind.factory().get();
        kind.projectSetter().accept(entity, context.projectSummary());
        kind.typeSetter().accept(entity, type);
        // Required by TraceableEntity (NOT NULL), which the services do not fill in.
        entity.setCreatedBy(context.userInfo().getUser());
        entity.setCreatedByInstitution(context.institution());
        return entity;
    }

    /**
     * A creation form's identifier suggestion: {@code suggestion} gets the new entity of the type in the
     * project, and runs in the caller's context — the identifier format is read in it. No permission is
     * required: nothing is written, and the project is already in the caller's scope.
     */
    @Transactional(readOnly = true)
    public <D extends AbstractEntityDTO> String suggestIdentifier(Kind<D> kind, String projectId, String typeId,
                                                                  PersonDTO personDto, Set<Long> accessibleInstitutionIds,
                                                                  String lang, Function<D, String> suggestion) {
        ProjectContext context = resolveProject(projectId, personDto, accessibleInstitutionIds, lang);
        D entity = newEntity(kind, context, resolveType(typeId, kind));
        return OpenApiExecutionContext.callWithUserInfo(context.userInfo(), () -> suggestion.apply(entity));
    }
}
