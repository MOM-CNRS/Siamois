package fr.siamois.domain.services.study;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.FilterDTO;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.StudySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.time.OffsetDateTime;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenSummaryDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.mapper.StudyMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

    @Mock private StudyRepository studyRepository;
    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private SpecimenRepository specimenRepository;
    @Mock private CustomFieldAnswerService customFieldAnswerService;
    @Mock private StudyMapper studyMapper;
    @Mock private EntityIdentifierGenerator identifierGenerator;
    @Mock private ProfilePermissionService profilePermissionService;

    @Mock
    private fr.siamois.domain.services.EntityLinkGuard entityLinkGuard;

    @InjectMocks
    private StudyService studyService;

    private Study study;
    private StudyDTO studyDTO;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        ExecutionContextHolder.set(new UserInfo(institution, new PersonDTO(), "fr"));
        lenient().when(profilePermissionService.hasProjectPermission(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        study = new Study();
        study.setId(100L);
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(7L);
        study.setActionUnit(actionUnit);
        studyDTO = new StudyDTO();
        studyDTO.setId(100L);
    }

    @AfterEach
    void clearExecutionContext() {
        ExecutionContextHolder.clear();
    }

    @Test
    void save_checksTheStudyEditRightsOnTheStudyProject() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        StudyDTO saved = studyService.save(studyDTO);

        assertSame(studyDTO, saved);
        verify(profilePermissionService).hasProjectPermission(any(), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_STUDIES),
                eq(PermissionConstants.ORGANIZATION_EDIT_STUDIES),
                eq(PermissionConstants.PROJECT_EDIT_STUDIES));
    }

    @Test
    void save_isForbiddenWithoutExecutionContext() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        ExecutionContextHolder.clear();

        assertThrows(ForbiddenOperationException.class, () -> studyService.save(studyDTO));

        verify(studyRepository, never()).save(any(Study.class));
    }

    @Test
    void save_isForbiddenWhenTheRightIsMissing() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(profilePermissionService.hasProjectPermission(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> studyService.save(studyDTO));

        verify(studyRepository, never()).save(any(Study.class));
    }

    @Test
    void save_resolvesTheLinkedRecordingUnitsAndFinds() {
        RecordingUnitSummaryDTO unitSummary = new RecordingUnitSummaryDTO();
        unitSummary.setId(11L);
        SpecimenSummaryDTO specimenSummary = new SpecimenSummaryDTO();
        specimenSummary.setId(22L);
        studyDTO.setRecordingUnits(Set.of(unitSummary));
        studyDTO.setSpecimens(Set.of(specimenSummary));
        RecordingUnit unit = new RecordingUnit();
        unit.setId(11L);
        Specimen specimen = new Specimen();
        specimen.setId(22L);
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(recordingUnitRepository.findAllById(Set.of(11L))).thenReturn(List.of(unit));
        when(specimenRepository.findAllById(Set.of(22L))).thenReturn(List.of(specimen));
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        studyService.save(studyDTO);

        ArgumentCaptor<Study> saved = ArgumentCaptor.forClass(Study.class);
        verify(studyRepository).save(saved.capture());
        assertEquals(Set.of(unit), saved.getValue().getRecordingUnits());
        assertEquals(Set.of(specimen), saved.getValue().getSpecimens());
    }

    @Test
    void save_withoutLinksEmptiesTheStudyLinks() {
        study.getRecordingUnits().add(new RecordingUnit());
        studyDTO.setRecordingUnits(null);
        studyDTO.setSpecimens(null);
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        studyService.save(studyDTO);

        assertTrue(study.getRecordingUnits().isEmpty());
        assertTrue(study.getSpecimens().isEmpty());
    }

    @Test
    void save_numbersTheStudyFromTheStudyTableFormat() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        studyService.save(studyDTO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IdentifierGenerationSpec<Study>> spec = ArgumentCaptor.forClass(IdentifierGenerationSpec.class);
        verify(identifierGenerator).generateIdentifierIfRequired(eq(study), spec.capture());
        assertEquals(ConfigurableTable.STUDY, spec.getValue().table());
        assertTrue(spec.getValue().generationRequired().test(new Study()), "a new study gets an identifier");
        assertFalse(spec.getValue().generationRequired().test(study), "a stored one keeps its own");
    }

    @Test
    void identifierSpec_readsTheStudyTypeProjectAndCollisions() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);
        study.getActionUnit().setFullIdentifier("PRJ-7");
        fr.siamois.domain.models.vocabulary.Concept type = new fr.siamois.domain.models.vocabulary.Concept();
        type.setId(33L);
        study.setType(type);
        when(studyRepository.existsByActionUnitIdAndIdentifier(7L, "ETU0001")).thenReturn(true);

        studyService.save(studyDTO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IdentifierGenerationSpec<Study>> spec = ArgumentCaptor.forClass(IdentifierGenerationSpec.class);
        verify(identifierGenerator).generateIdentifierIfRequired(eq(study), spec.capture());
        IdentifierGenerationSpec<Study> captured = spec.getValue();
        assertEquals(33L, captured.typeId().apply(study));
        assertNull(captured.typeId().apply(new Study()));
        assertEquals("PRJ-7", captured.displayValues().get("ID_UA").apply(study));
        assertTrue(captured.identifierAlreadyUsed().test(study, "ETU0001"));
        assertFalse(captured.identifierAlreadyUsed().test(study, "ETU0002"));
        captured.numberSetter().accept(study, 12);
        captured.identifierSetter().accept(study, "ETU0012");
        assertEquals(12, study.getGeneratedNumber());
        assertEquals("ETU0012", study.getIdentifier());
    }

    @Test
    void identifierAlreadyExistInAction_ignoresTheStudyItself() {
        studyDTO.setIdentifier("ETU0001");
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        studyDTO.setActionUnit(project);
        when(studyRepository.findByActionUnitIdAndIdentifier(7L, "ETU0001")).thenReturn(List.of(study));

        assertFalse(studyService.identifierAlreadyExistInAction(studyDTO));

        Study other = new Study();
        other.setId(101L);
        when(studyRepository.findByActionUnitIdAndIdentifier(7L, "ETU0001")).thenReturn(List.of(other));
        assertTrue(studyService.identifierAlreadyExistInAction(studyDTO));
    }

    @Test
    void searchStudies_scopesToTheInstitutionAndConvertsThePage() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO();
        Pageable pageable = PageRequest.of(0, 10);
        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(study)));
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        Page<StudyDTO> page = studyService.searchStudies(institution, filters, pageable);

        assertEquals(List.of(studyDTO), page.getContent());
    }

    @Test
    void searchStudies_acceptsTheNamedFilters() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        Pageable pageable = PageRequest.of(0, 10);
        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        FilterDTO byIdentifier = new FilterDTO();
        byIdentifier.add(StudySpec.IDENTIFIER_FILTER, "ETU", FilterDTO.FilterType.CONTAINS);
        byIdentifier.add(StudySpec.ACTION_UNIT_FILTER, List.of(7L), FilterDTO.FilterType.CONTAINS);
        FilterDTO global = new FilterDTO();
        global.add(ActionUnitSpec.GLOBAL_FILTER, "ETU", FilterDTO.FilterType.CONTAINS);

        assertTrue(studyService.searchStudies(institution, byIdentifier, pageable).isEmpty());
        assertTrue(studyService.searchStudies(institution, global, pageable).isEmpty());
        verify(studyRepository, times(2)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void countSearchResults_countsTheSameSpecification() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        when(studyRepository.count(any(Specification.class))).thenReturn(4L);

        assertEquals(4, studyService.countSearchResults(institution, new FilterDTO()));
    }

    @Test
    void countByActionContext_countsTheProjectStudies() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(7L);
        when(studyRepository.countByActionUnitId(7L)).thenReturn(5);

        assertEquals(5, studyService.countByActionContext(project));
    }

    @Test
    void identifierAlreadyExistInAction_isFalseWithoutAProject() {
        assertFalse(studyService.identifierAlreadyExistInAction(new StudyDTO()));
    }

    @Test
    void findById_convertsAKnownStudy() {
        when(studyRepository.findById(100L)).thenReturn(Optional.of(study));
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        assertSame(studyDTO, studyService.findById(100L));
    }

    @Test
    void findNextByActionUnit_returnsTheNextOneOrWrapsToTheOldest() {
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(10L);
        OffsetDateTime now = OffsetDateTime.now();
        studyDTO.setCreationTime(now);
        Study next = new Study();
        StudyDTO nextDto = new StudyDTO();
        when(studyRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(10L, now))
                .thenReturn(Optional.of(next), Optional.empty());
        when(studyMapper.convert(next)).thenReturn(nextDto);
        when(studyRepository.findFirstByActionUnitIdOrderByCreationTimeAsc(10L)).thenReturn(Optional.of(next));

        assertSame(nextDto, studyService.findNextByActionUnit(project, studyDTO));
        assertSame(nextDto, studyService.findNextByActionUnit(project, studyDTO));
    }

    @Test
    void findPreviousByActionUnit_returnsThePreviousOneOrWrapsToTheMostRecent() {
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(10L);
        OffsetDateTime now = OffsetDateTime.now();
        studyDTO.setCreationTime(now);
        Study previous = new Study();
        StudyDTO previousDto = new StudyDTO();
        when(studyRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(10L, now))
                .thenReturn(Optional.of(previous), Optional.empty());
        when(studyMapper.convert(previous)).thenReturn(previousDto);
        when(studyRepository.findFirstByActionUnitIdOrderByCreationTimeDesc(10L)).thenReturn(Optional.of(previous));

        assertSame(previousDto, studyService.findPreviousByActionUnit(project, studyDTO));
        assertSame(previousDto, studyService.findPreviousByActionUnit(project, studyDTO));
    }

    @Test
    void siblings_failWhenTheProjectHasNoStudyAtAll() {
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(10L);
        studyDTO.setCreationTime(OffsetDateTime.now());
        when(studyRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(eq(10L), any()))
                .thenReturn(Optional.empty());
        when(studyRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(eq(10L), any()))
                .thenReturn(Optional.empty());
        when(studyRepository.findFirstByActionUnitIdOrderByCreationTimeAsc(10L)).thenReturn(Optional.empty());
        when(studyRepository.findFirstByActionUnitIdOrderByCreationTimeDesc(10L)).thenReturn(Optional.empty());

        assertThrows(ActionUnitNotFoundException.class, () -> studyService.findNextByActionUnit(project, studyDTO));
        assertThrows(ActionUnitNotFoundException.class, () -> studyService.findPreviousByActionUnit(project, studyDTO));
    }

    @Test
    void save_withAdditionalAnswersPersistsThemForTheSavedStudy() {
        when(studyMapper.invertConvert(studyDTO)).thenReturn(study);
        when(studyRepository.save(study)).thenReturn(study);
        when(studyMapper.convert(study)).thenReturn(studyDTO);

        studyService.save(studyDTO, java.util.Map.of());

        verify(customFieldAnswerService).saveAdditionalFieldAnswers(studyDTO, java.util.Map.of());
    }

    @Test
    void findById_returnsNullForAnUnknownStudy() {
        when(studyRepository.findById(5L)).thenReturn(Optional.empty());

        assertNull(studyService.findById(5L));
    }

    @Test
    void deleteById_unlinked_deletesEntityAndAnswers() {
        Study target = new Study();
        target.setId(4L);
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(9L);
        target.setActionUnit(au);
        when(studyRepository.findById(4L)).thenReturn(java.util.Optional.of(target));
        when(profilePermissionService.hasDeleteEntitiesPermission(any(), eq(9L))).thenReturn(true);

        studyService.deleteById(4L);

        verify(entityLinkGuard).requireNoLinks(fr.siamois.domain.services.EntityLinkGuard.Kind.STUDY, 4L, "l'étude");
        verify(customFieldAnswerService).deleteAdditionalFieldAnswers(any(StudyDTO.class));
        verify(studyRepository).delete(target);
    }

    @Test
    void deleteById_linked_throwsAndDeletesNothing() {
        Study target = new Study();
        target.setId(4L);
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(9L);
        target.setActionUnit(au);
        when(studyRepository.findById(4L)).thenReturn(java.util.Optional.of(target));
        when(profilePermissionService.hasDeleteEntitiesPermission(any(), eq(9L))).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("liens")).when(entityLinkGuard)
                .requireNoLinks(fr.siamois.domain.services.EntityLinkGuard.Kind.STUDY, 4L, "l'étude");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> studyService.deleteById(4L));

        verify(studyRepository, never()).delete(any(Study.class));
    }

    @Test
    void deleteById_withoutPermission_throwsForbidden() {
        Study target = new Study();
        target.setId(4L);
        fr.siamois.domain.models.actionunit.ActionUnit au = new fr.siamois.domain.models.actionunit.ActionUnit();
        au.setId(9L);
        target.setActionUnit(au);
        when(studyRepository.findById(4L)).thenReturn(java.util.Optional.of(target));
        when(profilePermissionService.hasDeleteEntitiesPermission(any(), eq(9L))).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException.class,
                () -> studyService.deleteById(4L));

        verify(studyRepository, never()).delete(any(Study.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestIdentifier_peeksForTheMappedNewEntity_withoutWritingAnything() {
        fr.siamois.dto.entity.StudyDTO dto = new fr.siamois.dto.entity.StudyDTO();
        fr.siamois.domain.models.study.Study entity = new fr.siamois.domain.models.study.Study();
        when(studyMapper.invertConvert(dto)).thenReturn(entity);
        when(identifierGenerator.suggestIdentifier(org.mockito.ArgumentMatchers.eq(entity), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new fr.siamois.domain.services.identifier.GeneratedIdentifier(3, "SUGGESTED-3"));

        org.junit.jupiter.api.Assertions.assertEquals("SUGGESTED-3", studyService.suggestIdentifier(dto));

        ArgumentCaptor<fr.siamois.domain.services.identifier.IdentifierGenerationSpec<fr.siamois.domain.models.study.Study>> specCaptor = ArgumentCaptor.forClass(fr.siamois.domain.services.identifier.IdentifierGenerationSpec.class);
        verify(identifierGenerator).suggestIdentifier(org.mockito.ArgumentMatchers.eq(entity), specCaptor.capture());
        // Same spec as the save's generation, so the suggestion is what the creation would allocate.
        org.junit.jupiter.api.Assertions.assertEquals(fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.STUDY, specCaptor.getValue().table());
        verify(identifierGenerator, never()).generateIdentifierIfRequired(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(studyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
