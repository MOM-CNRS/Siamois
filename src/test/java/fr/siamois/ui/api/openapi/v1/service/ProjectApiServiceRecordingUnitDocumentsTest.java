package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectApiServiceRecordingUnitDocumentsTest {

    @Mock
    private InstitutionService institutionService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private DocumentService documentService;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    @Mock
    private FindOpenApiMapper findOpenApiMapper;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;

    private ProjectApiService projectApiService;

    private final PersonDTO personDto = new PersonDTO();
    private static final Set<Long> SCOPE = Set.of(10L, 20L);

    @BeforeEach
    void setUp() {
        personDto.setId(1L);
        projectApiService = new ProjectApiService(
                institutionService,
                actionUnitService,
                recordingUnitService,
                spatialUnitService,
                documentService,
                specimenService,
                projectDocumentOpenApiMapper,
                findOpenApiMapper,
                personMapper,
                permissionService,
                conceptService,
                conceptMapper,
                recordingUnitOpenApiService);
    }

    private ProjectApiCaller caller() {
        return new ProjectApiCaller(personDto, SCOPE, List.of());
    }

    @Test
    void listDocumentsForAccessibleRecordingUnit_resolvesRuAndMapsDocumentsSortedById() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(99L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("UE-KEY"), eq(SCOPE), isNull())).thenReturn(ru);

        Document docHighId = mock(Document.class);
        when(docHighId.getId()).thenReturn(30L);
        Document docLowId = org.mockito.Mockito.mock(Document.class);
        when(docLowId.getId()).thenReturn(7L);
        when(documentService.findForRecordingUnit(same(ru))).thenReturn(List.of(docHighId, docLowId));

        DocumentResource rLow = new DocumentResource();
        rLow.setId("7");
        DocumentResource rHigh = new DocumentResource();
        rHigh.setId("30");
        when(projectDocumentOpenApiMapper.toResource(same(docLowId))).thenReturn(rLow);
        when(projectDocumentOpenApiMapper.toResource(same(docHighId))).thenReturn(rHigh);

        List<DocumentResource> out = projectApiService.listDocumentsForAccessibleRecordingUnit(caller(), "UE-KEY");

        assertThat(out).extracting(DocumentResource::getId).containsExactly("7", "30");
        verify(recordingUnitService).findAccessibleRecordingUnitByKey("UE-KEY", SCOPE, null);
        verify(documentService).findForRecordingUnit(ru);
    }

    @Test
    void listDocumentsForAccessibleRecordingUnit_empty_returnsEmptyList() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(1L);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("1"), eq(SCOPE), isNull())).thenReturn(ru);
        when(documentService.findForRecordingUnit(same(ru))).thenReturn(List.of());

        List<DocumentResource> out = projectApiService.listDocumentsForAccessibleRecordingUnit(caller(), "1");

        assertThat(out).isEmpty();
    }

    @Test
    void listDocumentsForAccessibleRecordingUnit_whenRuNotFound_propagates() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("missing"), eq(SCOPE), isNull()))
                .thenThrow(new RecordingUnitNotFoundException("gone"));

        var callerDto = caller();
        assertThatThrownBy(() -> projectApiService.listDocumentsForAccessibleRecordingUnit(callerDto, "missing"))
                .isInstanceOf(RecordingUnitNotFoundException.class)
                .hasMessageContaining("gone");
    }
}
