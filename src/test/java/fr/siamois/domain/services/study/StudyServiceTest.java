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
    void findById_returnsNullForAnUnknownStudy() {
        when(studyRepository.findById(5L)).thenReturn(Optional.empty());

        assertNull(studyService.findById(5L));
    }
}
