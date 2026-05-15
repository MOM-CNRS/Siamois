package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectApiServiceRecordingUnitFindsTest {

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

    private ProjectApiService projectApiService;

    private final PersonDTO personDto = new PersonDTO();
    private static final Set<Long> SCOPE = Set.of(10L);

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
                conceptMapper);
    }

    private ProjectApiCaller caller() {
        return new ProjectApiCaller(personDto, SCOPE, List.of());
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_callsSpecimenServiceMapsPageAndUsesFrenchLangByDefault() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(55L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("KEY"), eq(SCOPE), isNull())).thenReturn(ru);

        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(100L);
        PageImpl<SpecimenDTO> specimenPage = new PageImpl<>(
                List.of(spec),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTime")),
                1L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(55L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any(Pageable.class)))
                .thenReturn(specimenPage);

        FindResource mapped = new FindResource();
        when(findOpenApiMapper.toResource(same(spec))).thenReturn(mapped);

        Page<FindResource> out = projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "KEY", 0, 20, "creationTime:desc", null);

        assertThat(out.getTotalElements()).isEqualTo(1L);
        assertThat(out.getContent()).containsExactly(mapped);
        verify(findOpenApiMapper).toResource(same(spec));
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_respectsAcceptLanguageForQuery() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(1L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("1"), eq(SCOPE), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> specimenPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(1L), isNull(), isNull(), isNull(), eq("it"), eq("id:asc"), any(Pageable.class)))
                .thenReturn(specimenPage);

        projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "1", 0, 10, "id:asc", "it-CH,it;q=0.9");

        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(1L), isNull(), isNull(), isNull(), eq("it"), eq("id:asc"), any(Pageable.class));
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_passesSortParamToSpecimenService() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(2L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("2"), eq(SCOPE), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> specimenPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(2L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any(Pageable.class)))
                .thenReturn(specimenPage);

        projectApiService.pageFindsForAccessibleRecordingUnit(caller(), "2", 0, 10, "creationTime:desc", null);

        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(2L), isNull(), isNull(), isNull(), eq("fr"), eq("creationTime:desc"), any(Pageable.class));
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_offsetPageUsesCorrectPageIndex() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(2L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("2"), eq(SCOPE), isNull())).thenReturn(ru);

        PageImpl<SpecimenDTO> specimenPage = new PageImpl<>(List.of(), PageRequest.of(2, 10), 0L);
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(2L), isNull(), isNull(), isNull(), eq("fr"), isNull(), any(Pageable.class)))
                .thenReturn(specimenPage);

        projectApiService.pageFindsForAccessibleRecordingUnit(caller(), "2", 20, 10, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(10L), eq(2L), isNull(), isNull(), isNull(), eq("fr"), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_whenRuNotFound_propagates() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("x"), eq(SCOPE), isNull()))
                .thenThrow(new RecordingUnitNotFoundException("gone"));

        assertThatThrownBy(() -> projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "x", 0, 10, null, null))
                .isInstanceOf(RecordingUnitNotFoundException.class)
                .hasMessageContaining("gone");
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_whenNoInstitution_throwsBadRequest() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(8L);
        ru.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("8"), eq(SCOPE), isNull())).thenReturn(ru);

        assertThatThrownBy(() -> projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "8", 0, 10, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_whenInstitutionIdNull_throwsBadRequest() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(null);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(9L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("9"), eq(SCOPE), isNull())).thenReturn(ru);

        assertThatThrownBy(() -> projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "9", 0, 10, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void pageFindsForAccessibleRecordingUnit_whenRecordingUnitIdNull_throwsBadRequest() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(null);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey(eq("ru"), eq(SCOPE), isNull())).thenReturn(ru);

        assertThatThrownBy(() -> projectApiService.pageFindsForAccessibleRecordingUnit(
                caller(), "ru", 0, 10, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }
}
