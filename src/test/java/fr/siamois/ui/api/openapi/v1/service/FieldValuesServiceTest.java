package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldValuesServiceTest {

    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final PhaseOpenApiService phaseOpenApiService = mock(PhaseOpenApiService.class);
    private final RelationFieldService relationFieldService = mock(RelationFieldService.class);
    private final PhaseListProjectionService phaseListProjectionService = mock(PhaseListProjectionService.class);
    private final CustomFieldRepository customFieldRepository = mock(CustomFieldRepository.class);
    private final ProjectApiCaller caller = new ProjectApiCaller(new PersonDTO(), Set.of(1L), List.of());

    private FieldValuesService service;

    private static final String PARENTS = fieldId(ConfigurableTable.UE, "parents");
    private static final String KEYWORDS = fieldId(ConfigurableTable.PHASE, "keywords");
    private static final String TITLE = fieldId(ConfigurableTable.PHASE, "title");

    @BeforeEach
    void setUp() {
        service = new FieldValuesService(projectApiService, mock(SpecimenService.class), phaseOpenApiService,
                mock(ContainerOpenApiService.class), relationFieldService, mock(RecordingUnitListProjectionService.class),
                mock(FindListProjectionService.class), phaseListProjectionService, mock(ContainerListProjectionService.class),
                mock(ProjectListProjectionService.class), customFieldRepository);
        PhaseDTO phase = new PhaseDTO();
        phase.setId(7L);
        when(phaseOpenApiService.requireAccessible(eq(7L), any(), any())).thenReturn(phase);
    }

    @Test
    void aRelationField_isPagedInTheDatabase() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(42L);
        when(projectApiService.requireViewableRecordingUnit(caller, "42")).thenReturn(ru);
        RelationFieldService.ValuesPage page = new RelationFieldService.ValuesPage(List.of(ref("1", "US 1")), 37);
        when(relationFieldService.page(RelationField.RECORDING_UNIT_PARENTS, 42L, 50, 50, "us", false, "fr")).thenReturn(page);

        assertThat(service.values(caller, "recording-units", "42", PARENTS, 50, 50, "us", "label:desc", "fr")).isSameAs(page);
    }

    @Test
    void anyOtherMultiValuedField_isReadWhole_thenFilteredSortedAndPaged() {
        when(phaseListProjectionService.build(any(), eq(KEYWORDS), eq("fr"), eq(ValuesLimit.UNLIMITED)))
                .thenReturn(new PhaseListProjectionService.PhaseListProjection(Map.of(), Map.of(7L, Map.of(KEYWORDS,
                        MultiValue.complete(List.of(ref("1", "Fossé"), ref("2", "fosse"), ref("3", "Mur"), ref("4", "Foyer")))))));

        RelationFieldService.ValuesPage page = service.values(caller, "phases", "7", KEYWORDS, 0, 2, "fo", "label:asc", "fr");

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.values()).extracting(ResourceRef::label).containsExactly("fosse", "Fossé");
    }

    @Test
    void anUnansweredMultiValuedField_hasNoValue() {
        when(phaseListProjectionService.build(any(), eq(KEYWORDS), eq("fr"), eq(ValuesLimit.UNLIMITED)))
                .thenReturn(new PhaseListProjectionService.PhaseListProjection(Map.of(), Map.of(7L, java.util.Collections.singletonMap(KEYWORDS, null))));

        assertThat(service.values(caller, "phases", "7", KEYWORDS, 0, 50, null, "label:asc", "fr").total()).isZero();
    }

    @Test
    void rejectsAScalarField_anUnknownField_anUnknownCollection_andABadSort() {
        when(customFieldRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.values(caller, "phases", "7", TITLE, 0, 50, null, "label:asc", "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.values(caller, "phases", "7", "123456", 0, 50, null, "label:asc", "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.values(caller, "documents", "7", KEYWORDS, 0, 50, null, "label:asc", "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.values(caller, "phases", "7", KEYWORDS, 0, 50, null, "id:asc", "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static String fieldId(ConfigurableTable table, String binding) {
        return String.valueOf(SystemFieldCatalog.fieldBoundTo(table, binding).getId());
    }

    private static ResourceRef ref(String id, String label) {
        return new ResourceRef(id, "concepts", label);
    }
}
