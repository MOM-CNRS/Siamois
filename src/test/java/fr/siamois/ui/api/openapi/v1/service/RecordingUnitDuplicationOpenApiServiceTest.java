package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitIdentifierAlreadyExistsException;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitStructureDuplicationResult;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitDuplicationResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitStructureResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingUnitDuplicationOpenApiServiceTest {

    private static final Set<Long> INSTITUTIONS = Set.of(100L);

    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private ProfilePermissionService profilePermissionService;

    private RecordingUnitDuplicationOpenApiService service;
    private RecordingUnitDTO root;
    private final PersonDTO person = new PersonDTO();

    @BeforeEach
    void setUp() {
        service = new RecordingUnitDuplicationOpenApiService(recordingUnitService, profilePermissionService);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(100L);
        root = unit(1L, "UE-1");
        root.setCreatedByInstitution(institution);
    }

    private static RecordingUnitDTO unit(long id, String fullIdentifier) {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(id);
        dto.setFullIdentifier(fullIdentifier);
        return dto;
    }

    private void accessible() {
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity("1", INSTITUTIONS, null))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(null, root));
    }

    @Test
    void structure_listsDescendantsParentFirst_eachWithItsParent() {
        accessible();
        when(recordingUnitService.findAllByParentRecordingUnit(1L)).thenReturn(List.of(unit(2L, "UE-2"), unit(3L, "UE-3")));
        when(recordingUnitService.findAllByParentRecordingUnit(2L)).thenReturn(List.of(unit(4L, "UE-4")));
        when(recordingUnitService.findAllByParentRecordingUnit(3L)).thenReturn(List.of());
        when(recordingUnitService.findAllByParentRecordingUnit(4L)).thenReturn(List.of());

        RecordingUnitStructureResource structure = service.structure("1", INSTITUTIONS);

        assertThat(structure.root().label()).isEqualTo("UE-1");
        assertThat(structure.descendants()).extracting(RecordingUnitStructureResource.Node::id).containsExactly(2L, 3L, 4L);
        assertThat(structure.descendants()).extracting(RecordingUnitStructureResource.Node::parentId).containsExactly(1L, 1L, 2L);
        assertThat(structure.truncated()).isFalse();
    }

    @Test
    void structure_neverLoopsOnACycleInTheHierarchy() {
        accessible();
        when(recordingUnitService.findAllByParentRecordingUnit(1L)).thenReturn(List.of(unit(2L, "UE-2")));
        when(recordingUnitService.findAllByParentRecordingUnit(2L)).thenReturn(List.of(unit(1L, "UE-1")));

        assertThat(service.structure("1", INSTITUTIONS).descendants()).hasSize(1);
    }

    @Test
    void structure_stopsAtTheNodeLimitAndSaysSo() {
        accessible();
        List<RecordingUnitDTO> many = new java.util.ArrayList<>();
        for (long i = 2; i <= 502; i++) many.add(unit(i, "UE-" + i));
        when(recordingUnitService.findAllByParentRecordingUnit(1L)).thenReturn(many);

        RecordingUnitStructureResource structure = service.structure("1", INSTITUTIONS);

        assertThat(structure.descendants()).hasSize(RecordingUnitDuplicationOpenApiService.MAX_NODES);
        assertThat(structure.truncated()).isTrue();
    }

    @Test
    void duplicateStructure_createsTheRequestedCopiesOfTheRootAndTheSelection() {
        accessible();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), eq(root))).thenReturn(true);
        when(recordingUnitService.duplicateStructure(eq(root), anySet(), eq(2))).thenReturn(
                new RecordingUnitStructureDuplicationResult(
                        List.of(unit(10L, "UE-10"), unit(11L, "UE-11")),
                        List.of(unit(10L, "UE-10"), unit(11L, "UE-11"), unit(12L, "UE-12"), unit(13L, "UE-13"))));

        RecordingUnitDuplicationResource result = service.duplicateStructure("1", 2, List.of(2L), person, INSTITUTIONS, "fr");

        ArgumentCaptor<Set<Long>> selected = ArgumentCaptor.forClass(Set.class);
        verify(recordingUnitService).duplicateStructure(eq(root), selected.capture(), eq(2));
        assertThat(selected.getValue()).containsExactly(2L);
        assertThat(result.copies()).extracting(RecordingUnitDuplicationResource.Copy::id).containsExactly(10L, 11L);
        assertThat(result.createdCount()).isEqualTo(4);
    }

    @Test
    void duplicateStructure_defaultsToOneCopyOfTheRootAlone() {
        accessible();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), eq(root))).thenReturn(true);
        when(recordingUnitService.duplicateStructure(eq(root), anySet(), eq(1))).thenReturn(
                new RecordingUnitStructureDuplicationResult(List.of(unit(10L, "UE-10")), List.of(unit(10L, "UE-10"))));

        assertThat(service.duplicateStructure("1", null, null, person, INSTITUTIONS, "fr").createdCount()).isEqualTo(1);
    }

    @Test
    void duplicateStructure_refusesWithoutWriteRight() {
        accessible();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), eq(root))).thenReturn(false);

        assertThatThrownBy(() -> service.duplicateStructure("1", 1, List.of(), person, INSTITUTIONS, "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        verify(recordingUnitService, never()).duplicateStructure(any(), anySet(), anyInt());
    }

    @Test
    void duplicateStructure_rejectsACopyCountOutOfRange() {
        assertThatThrownBy(() -> service.duplicateStructure("1", 0, List.of(), person, INSTITUTIONS, "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.duplicateStructure("1", 51, List.of(), person, INSTITUTIONS, "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void duplicateStructure_rejectsMoreCreationsThanTheLimit() {
        accessible();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), eq(root))).thenReturn(true);
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 2; i <= 12; i++) ids.add(i);

        // 11 units × 50 copies = 550 > 500
        assertThatThrownBy(() -> service.duplicateStructure("1", 50, ids, person, INSTITUTIONS, "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verify(recordingUnitService, never()).duplicateStructure(any(), anySet(), anyInt());
    }

    @Test
    void duplicateStructure_reportsATakenIdentifierAsAConflict() {
        accessible();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), eq(root))).thenReturn(true);
        when(recordingUnitService.duplicateStructure(eq(root), anySet(), eq(1)))
                .thenThrow(new RecordingUnitIdentifierAlreadyExistsException("UE-9"));

        assertThatThrownBy(() -> service.duplicateStructure("1", 1, List.of(), person, INSTITUTIONS, "fr"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
}
