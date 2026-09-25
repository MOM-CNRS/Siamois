package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.LangService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.response.organization.OrganizationFieldCatalogResponse;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationFieldCatalogServiceTest {

    private final CustomFieldRepository customFieldRepository = mock(CustomFieldRepository.class);
    private final FieldQueryService fieldQueryService = mock(FieldQueryService.class);
    private final LangService langService = mock(LangService.class);
    private final OrganizationFieldCatalogService service =
            new OrganizationFieldCatalogService(customFieldRepository, fieldQueryService, langService);

    @BeforeEach
    void setUp() {
        when(langService.localeForApiLang(any())).thenReturn(Locale.FRENCH);
        when(langService.resolveMessage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(fieldQueryService.withQuery(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CustomField additional(long id, String label) {
        CustomFieldText field = new CustomFieldText();
        field.setId(id);
        field.setLabel(label);
        field.setIsSystemField(false);
        return field;
    }

    @Test
    void systemFieldsFirst_thenTheUnionOfTheProjectsAdditionalFields() {
        when(customFieldRepository.findActiveAdditionalByInstitutionAndTable(7L, ConfigurableTable.UE.getFieldCode()))
                .thenReturn(List.of(additional(501L, "Couleur"), additional(502L, "Texture")));

        OrganizationFieldCatalogResponse response = service.build(7L, ConfigurableTable.UE, "fr");

        List<String> systemIds = SystemFieldCatalog.fieldsOf(ConfigurableTable.UE).stream()
                .map(f -> String.valueOf(f.getId())).toList();
        assertThat(response.getFields().keySet()).startsWith(systemIds.toArray(String[]::new)).endsWith("501", "502");
        assertThat(response.getDefaultType().fields()).isSameAs(response.getFields());
        assertThat(response.getData()).isEmpty();
        // The recording-unit list keeps its server-driven default columns.
        assertThat(response.getDefaultType().tableColumns()).isNotEmpty();
    }

    @Test
    void eachFieldCarriesWhatTheListAcceptsOnIt_forTheTablesEntity() {
        when(customFieldRepository.findActiveAdditionalByInstitutionAndTable(eq(7L), any())).thenReturn(List.of());

        service.build(7L, ConfigurableTable.PHASE, "fr");

        org.mockito.Mockito.verify(fieldQueryService, org.mockito.Mockito.atLeastOnce())
                .withQuery(any(FieldResource.class), eq(Phase.class), any());
    }

    @Test
    void otherTablesHaveNoDefaultColumns() {
        when(customFieldRepository.findActiveAdditionalByInstitutionAndTable(eq(7L), any())).thenReturn(List.of());

        assertThat(service.build(7L, ConfigurableTable.CONTENANT, "fr").getDefaultType().tableColumns()).isNull();
        assertThat(OrganizationFieldCatalogService.entityTypeOf(ConfigurableTable.UE)).isEqualTo(RecordingUnit.class);
    }
}
