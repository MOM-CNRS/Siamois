package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormServiceTest {

    @Mock
    private FormRepository formRepository;

    @InjectMocks
    private FormService formService;

    private Concept recordingUnitType;
    private Institution institution;


    void setUp() {
        recordingUnitType = mock(Concept.class);
        institution = mock(Institution.class);

        // Use deterministic IDs in stubs
        given(recordingUnitType.getId()).willReturn(101L);
        given(institution.getId()).willReturn(55L);
    }

    @Test
    void findAllFieldsBySpatialUnitId_success() {

        when(formRepository.findById(anyLong()))
                .thenReturn(Optional.of(new CustomForm()));

        // act
        CustomForm res = formService.findById(anyLong());

        assertNotNull(res);
        assertInstanceOf(CustomForm.class, res);

    }

    @Test
    void findAllFieldsBySpatialUnitId_null() {

        when(formRepository.findById(anyLong()))
                .thenReturn(Optional.empty());
        // act
        CustomForm res = formService.findById(anyLong());
        assertNull(res);

    }

    @Test
    void returnsTypeSpecificFormWhenPresent() {
        // arrange
        setUp();
        CustomForm typeSpecific = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.of(typeSpecific));

        // act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        // assert
        assertSame(typeSpecific, result, "Should return the type-specific form");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        // Ensure no fallback call when first lookup succeeds
        verify(formRepository, never()).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }

    @Test
    void fallsBackToInstitutionOnlyWhenTypeSpecificMissing() {
        // arrange
        setUp();
        CustomForm fallback = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.of(fallback));

        // act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        // assert
        assertSame(fallback, result, "Should return the institution-only form on fallback");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        verify(formRepository).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }

    @Test
    void returnsNullWhenNothingFound() {
        // arrange
        setUp();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.empty());

        // act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        // assert
        assertNull(result, "Should return null when neither lookup finds a form");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        verify(formRepository).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }
}