package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.LangService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import fr.siamois.ui.api.openapi.v1.response.organization.OrganizationFieldCatalogResponse;
import fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Column catalogs of the organization-wide lists (recording units, finds, phases, containers).
 * <p>
 * A form configuration belongs to a project, and an organization-wide list spans them all: its
 * catalog is the table's system fields — the same in every project, from its details form — plus the
 * union of the additional fields active in at least one of the organization's projects. Sorting,
 * filtering and projecting any of those ids on an organization-wide list needs no project either
 * ({@link FieldQueryService}, {@link AdditionalAnswersListProjector}); a row whose project lacks a
 * field simply has no answer for it.
 */
@Service
@RequiredArgsConstructor
public class OrganizationFieldCatalogService {

    private final CustomFieldRepository customFieldRepository;
    private final FieldQueryService fieldQueryService;
    private final LangService langService;

    @Transactional(readOnly = true)
    public OrganizationFieldCatalogResponse build(long organizationId, ConfigurableTable table, String lang) {
        Locale locale = langService.localeForApiLang(lang);
        Class<?> entityType = entityTypeOf(table);

        Map<String, FieldResource> fields = new LinkedHashMap<>();
        List<CustomField> catalog = new ArrayList<>(SystemFieldCatalog.fieldsOf(table));
        catalog.addAll(customFieldRepository.findActiveAdditionalByInstitutionAndTable(organizationId, table.getFieldCode()));
        for (CustomField field : catalog) {
            if (field == null || field.getId() == null) continue;
            FieldResource resource = FieldAnswerWireService.fieldResourceOf(field,
                    langService.resolveMessage(field.getLabel(), locale),
                    langService.resolveMessage(field.getHint(), locale));
            fields.putIfAbsent(String.valueOf(field.getId()), fieldQueryService.withQuery(resource, entityType, field));
        }
        return new OrganizationFieldCatalogResponse(fields, table == ConfigurableTable.UE ? recordingUnitTableColumns() : null);
    }

    static Class<?> entityTypeOf(ConfigurableTable table) {
        return switch (table) {
            case UE -> RecordingUnit.class;
            case MOBILIER -> Specimen.class;
            case PHASE -> Phase.class;
            case CONTENANT -> Container.class;
        };
    }

    /** Same defaults as the project catalog's {@code _default.tableColumns} (RecordingUnitOpenApiService). */
    private static List<ProjectTableColumnResource> recordingUnitTableColumns() {
        List<RecordingUnitTableColumnDefaults.ColumnDefault> defaults = RecordingUnitTableColumnDefaults.columns();
        List<ProjectTableColumnResource> out = new ArrayList<>(defaults.size());
        for (int i = 0; i < defaults.size(); i++) {
            RecordingUnitTableColumnDefaults.ColumnDefault d = defaults.get(i);
            out.add(new ProjectTableColumnResource(d.columnId(), d.fieldId(), d.visible(), i));
        }
        return out;
    }
}
