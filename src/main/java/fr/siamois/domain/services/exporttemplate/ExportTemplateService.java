package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.DuplicateExportTemplateException;
import fr.siamois.domain.models.exporttemplate.ExportTemplate;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Sheet;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.exporttemplate.ExportTemplateRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Gestion des modèles d'export d'une institution : liste, création, copie, import/export du JSON,
 * édition de la définition. Lire demande l'accès aux données de l'institution ; écrire demande la
 * gestion des paramètres (instance ou organisation).
 */
@Service
@RequiredArgsConstructor
public class ExportTemplateService {

    /** Modèles livrés avec l'application — liste fermée, jamais un chemin fourni par l'appelant. */
    public enum BuiltInTemplate {
        NATIONAL_REPORT("export-templates/rapport-operation-national.json"),
        /** Le même classeur, dont la feuille « situation » est celle du milieu sous-marin. */
        UNDERWATER_REPORT("export-templates/rapport-operation-national-sous-marin.json");

        private final String resource;

        BuiltInTemplate(String resource) {
            this.resource = resource;
        }
    }

    private final ExportTemplateRepository exportTemplateRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final ProfilePermissionService profilePermissionService;
    private final InstitutionMapper institutionMapper;
    private final PersonMapper personMapper;

    // ------------------------------------------------------------------ lecture

    /** Liste des modèles de l'institution ; les modèles livrés avec l'application y sont ajoutés, publiés, s'ils manquent. */
    @Transactional
    public List<ExportTemplateDTO> findAll(UserInfo userInfo) {
        assertCanView(userInfo);
        provisionBuiltIns(userInfo);
        return exportTemplateRepository.findByInstitutionOrderByNameAsc(institutionOf(userInfo)).stream()
                .map(ExportTemplateService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExportTemplateDTO find(UserInfo userInfo, Long id) {
        assertCanView(userInfo);
        return toDto(load(userInfo, id));
    }

    @Transactional(readOnly = true)
    public ExportTemplateDefinition getDefinition(UserInfo userInfo, Long id) {
        assertCanView(userInfo);
        return ExportTemplateJson.parse(load(userInfo, id).getDefinition());
    }

    /** JSON partageable d'un modèle (sans aucune donnée locale). */
    @Transactional(readOnly = true)
    public String exportJson(UserInfo userInfo, Long id) {
        assertCanView(userInfo);
        return load(userInfo, id).getDefinition();
    }

    // ------------------------------------------------------------------ création

    /** Modèle neuf : une feuille vide, sans source ni colonne. */
    @Transactional
    public ExportTemplateDTO createBlank(UserInfo userInfo, String name, String sheetName) {
        assertCanManage(userInfo);
        Sheet sheet = new Sheet(sheetName, false, List.of(), List.of(), List.of());
        ExportTemplateDefinition definition = new ExportTemplateDefinition(
                ExportTemplateDefinition.CURRENT_SCHEMA_VERSION, UUID.randomUUID().toString(), "1.0.0", name, null, List.of(sheet));
        return toDto(save(userInfo, definition));
    }

    /**
     * Importe un JSON (fichier saisi ou issu d'une instance centrale). Le JSON est validé avant
     * enregistrement ; si l'institution possède déjà un modèle de même identifiant global, l'import est
     * enregistré sous un nouvel identifiant.
     */
    @Transactional
    public ExportTemplateDTO importJson(UserInfo userInfo, String json) {
        assertCanManage(userInfo);
        return toDto(save(userInfo, ExportTemplateJson.parse(json)));
    }

    /**
     * Ajoute à l'institution les modèles livrés (référentiel national, milieu sous-marin) qu'elle n'a pas encore,
     * publiés donc en lecture seule : on les duplique ou on en fait une nouvelle version pour les adapter.
     */
    private void provisionBuiltIns(UserInfo userInfo) {
        Institution institution = institutionOf(userInfo);
        for (BuiltInTemplate builtIn : BuiltInTemplate.values()) {
            ExportTemplateDefinition definition = ExportTemplateJson.parse(readResource(builtIn.resource));
            if (exportTemplateRepository.existsByInstitutionAndTemplateUuid(institution, definition.id())
                    || exportTemplateRepository.existsByInstitutionAndNameIgnoreCaseAndVersion(institution, definition.name(), definition.version())) {
                continue;
            }
            ExportTemplate template = save(userInfo, definition);
            template.setPublished(true);
            exportTemplateRepository.save(template);
        }
    }

    @Transactional
    public ExportTemplateDTO duplicate(UserInfo userInfo, Long id, String newName) {
        assertCanManage(userInfo);
        ExportTemplate source = load(userInfo, id);
        ExportTemplateDefinition d = ExportTemplateJson.parse(source.getDefinition());
        Institution institution = source.getInstitution();
        String name = newName;
        for (int n = 2; exportTemplateRepository.existsByInstitutionAndNameIgnoreCaseAndVersion(institution, name, d.version()); n++) {
            name = newName + " " + n;
        }
        ExportTemplate copy = save(userInfo, new ExportTemplateDefinition(
                d.schemaVersion(), UUID.randomUUID().toString(), d.version(), name, d.fileNamePattern(), d.sheets()));
        copy.setReferenceProject(source.getReferenceProject());
        return toDto(exportTemplateRepository.save(copy));
    }

    /** Nouvelle version d'un modèle : même nom, nouvelle étiquette de version, non publiée. */
    @Transactional
    public ExportTemplateDTO newVersion(UserInfo userInfo, Long id) {
        assertCanManage(userInfo);
        ExportTemplate source = load(userInfo, id);
        ExportTemplateDefinition d = ExportTemplateJson.parse(source.getDefinition());
        String version = d.version();
        do {
            version = nextVersion(version);
        } while (exportTemplateRepository.existsByInstitutionAndNameIgnoreCaseAndVersion(source.getInstitution(), d.name(), version));
        ExportTemplate copy = save(userInfo, new ExportTemplateDefinition(
                d.schemaVersion(), UUID.randomUUID().toString(), version, d.name(), d.fileNamePattern(), d.sheets()));
        copy.setReferenceProject(source.getReferenceProject());
        return toDto(exportTemplateRepository.save(copy));
    }

    /** Étiquette suivante : le dernier nombre est incrémenté (1.0.0 → 1.0.1, v2 → v3), sinon « .1 » est ajouté. */
    static String nextVersion(String version) {
        if (version == null || version.isBlank()) {
            return "1.0.0";
        }
        int digitsStart = version.length();
        while (digitsStart > 0 && Character.isDigit(version.charAt(digitsStart - 1))) {
            digitsStart--;
        }
        if (digitsStart == version.length()) {
            return version + ".1";
        }
        return version.substring(0, digitsStart) + (Long.parseLong(version.substring(digitsStart)) + 1);
    }

    // ------------------------------------------------------------------ modification

    /** Remplace la définition ; l'identifiant global ne change jamais. */
    @Transactional
    public ExportTemplateDTO updateDefinition(UserInfo userInfo, Long id, ExportTemplateDefinition definition) {
        assertCanManage(userInfo);
        return replaceDefinition(loadEditable(userInfo, id), definition);
    }

    private ExportTemplateDTO replaceDefinition(ExportTemplate template, ExportTemplateDefinition definition) {
        if (!template.getTemplateUuid().equals(definition.id())) {
            throw new InvalidExportTemplateException("The template id cannot change");
        }
        assertUnique(template.getInstitution(), definition.name(), definition.version(), template.getId());
        // Un aller-retour par le codec garantit que ce qui est stocké respecte la grammaire.
        String json = ExportTemplateJson.toJson(ExportTemplateJson.parse(ExportTemplateJson.toJson(definition)));
        apply(template, definition, json);
        return toDto(exportTemplateRepository.save(template));
    }

    @Transactional
    public ExportTemplateDTO rename(UserInfo userInfo, Long id, String newName) {
        assertCanManage(userInfo);
        ExportTemplate template = loadEditable(userInfo, id);
        ExportTemplateDefinition d = ExportTemplateJson.parse(template.getDefinition());
        return replaceDefinition(template, new ExportTemplateDefinition(
                d.schemaVersion(), d.id(), d.version(), newName, d.fileNamePattern(), d.sheets()));
    }

    /** Le projet de référence est une donnée locale, absente du JSON partagé. */
    @Transactional
    public ExportTemplateDTO setReferenceProject(UserInfo userInfo, Long id, @Nullable Long projectId) {
        assertCanManage(userInfo);
        ExportTemplate template = loadEditable(userInfo, id);
        template.setReferenceProject(projectId == null ? null : projectOf(userInfo, projectId));
        return toDto(exportTemplateRepository.save(template));
    }

    /** Publie le modèle : il devient en lecture seule, pour tout appel, et ne reste que duplicable. */
    @Transactional
    public ExportTemplateDTO publish(UserInfo userInfo, Long id) {
        assertCanManage(userInfo);
        ExportTemplate template = load(userInfo, id);
        template.setPublished(true);
        return toDto(exportTemplateRepository.save(template));
    }

    @Transactional
    public void delete(UserInfo userInfo, Long id) {
        assertCanManage(userInfo);
        exportTemplateRepository.delete(load(userInfo, id));
    }

    // ------------------------------------------------------------------ interne

    private ExportTemplate save(UserInfo userInfo, ExportTemplateDefinition parsed) {
        Institution institution = institutionOf(userInfo);
        ExportTemplateDefinition definition = parsed;
        if (exportTemplateRepository.existsByInstitutionAndTemplateUuid(institution, definition.id())) {
            definition = new ExportTemplateDefinition(
                    definition.schemaVersion(), UUID.randomUUID().toString(), definition.version(),
                    definition.name(), definition.fileNamePattern(), definition.sheets());
        }
        assertUnique(institution, definition.name(), definition.version(), null);
        ExportTemplate template = new ExportTemplate();
        template.setInstitution(institution);
        template.setCreatedBy(personMapper.invertConvert(userInfo.getUser()));
        apply(template, definition, ExportTemplateJson.toJson(definition));
        return exportTemplateRepository.save(template);
    }

    /** La paire nom + version identifie un modèle dans l'institution. */
    private void assertUnique(Institution institution, String name, String version, @Nullable Long excludeId) {
        boolean taken = excludeId == null
                ? exportTemplateRepository.existsByInstitutionAndNameIgnoreCaseAndVersion(institution, name, version)
                : exportTemplateRepository.existsByInstitutionAndNameIgnoreCaseAndVersionAndIdNot(institution, name, version, excludeId);
        if (taken) {
            throw new DuplicateExportTemplateException(name, version);
        }
    }

    private static void apply(ExportTemplate template, ExportTemplateDefinition definition, String json) {
        template.setTemplateUuid(definition.id());
        template.setName(definition.name());
        template.setVersion(definition.version());
        template.setDefinition(json);
    }

    private ExportTemplate load(UserInfo userInfo, Long id) {
        return exportTemplateRepository.findByIdAndInstitution(id, institutionOf(userInfo))
                .orElseThrow(() -> new NoSuchElementException("Export template " + id + " not found"));
    }

    /** Charge un modèle à modifier : un modèle publié est refusé. */
    private ExportTemplate loadEditable(UserInfo userInfo, Long id) {
        ExportTemplate template = load(userInfo, id);
        if (template.isPublished()) {
            throw new ForbiddenOperationException("The export template " + id + " is published and read-only");
        }
        return template;
    }

    private ActionUnit projectOf(UserInfo userInfo, Long projectId) {
        ActionUnit project = actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project " + projectId + " not found"));
        if (!project.getCreatedByInstitution().getId().equals(userInfo.getInstitution().getId())) {
            throw new ForbiddenOperationException("The project belongs to another institution");
        }
        return project;
    }

    private Institution institutionOf(UserInfo userInfo) {
        return institutionMapper.invertConvert(userInfo.getInstitution());
    }

    private void assertCanView(UserInfo userInfo) {
        requireUser(userInfo);
        if (!profilePermissionService.canViewInstitutionData(userInfo.getUser(), userInfo.getInstitution())) {
            throw new ForbiddenOperationException("Cannot view the export templates of this institution");
        }
    }

    private static void requireUser(UserInfo userInfo) {
        if (userInfo == null) {
            throw new ForbiddenOperationException("No user in the session");
        }
    }

    private void assertCanManage(UserInfo userInfo) {
        requireUser(userInfo);
        boolean allowed = profilePermissionService.hasInstancePermission(
                userInfo.getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(
                userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
        if (!allowed) {
            throw new ForbiddenOperationException("Cannot manage the export templates of this institution");
        }
    }

    private static ExportTemplateDTO toDto(ExportTemplate t) {
        ActionUnit reference = t.getReferenceProject();
        return new ExportTemplateDTO(
                t.getId(), t.getTemplateUuid(), t.getName(), t.getVersion(),
                reference == null ? null : reference.getId(), t.getCreatedAt(), t.getUpdatedAt(), t.isPublished());
    }

    private static String readResource(String resource) {
        try (InputStream in = ExportTemplateService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Built-in export template missing: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read built-in export template " + resource, e);
        }
    }
}
