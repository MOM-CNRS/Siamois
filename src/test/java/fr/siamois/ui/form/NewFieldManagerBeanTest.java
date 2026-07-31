package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewFieldManagerBeanTest {

    @Mock private CustomFieldMeasurementService customFieldMeasurementService;
    @Mock private RecordingUnitService recordingUnitService;

    private CustomFormResponseViewModel formResponse;
    private CustomFieldMeasurement created;

    @BeforeEach
    void setUp() {
        formResponse = new CustomFormResponseViewModel();
        formResponse.setAnswers(new HashMap<>());

        created = new CustomFieldMeasurement();
        created.setId(42L);
    }

    private NewFieldManagerBean beanFor(AbstractEntityDTO owner, List<CustomFieldMeasurement> options) {
        return new NewFieldManagerBean(
                customFieldMeasurementService,
                recordingUnitService,
                formResponse,
                owner,
                options);
    }

    private void fillEditor(NewFieldManagerBean bean) {
        bean.prepareNewField(new CustomFormPanelUiDto());
        bean.setType(new ConceptAutocompleteDTO(new ConceptDTO(), "Longueur", "fr"));
    }

    @Test
    @DisplayName("A field created from a recording unit's form is attached to that unit")
    void saveNewField_linksCreatedFieldToRecordingUnit() {
        RecordingUnitDTO owner = new RecordingUnitDTO();
        owner.setId(7L);
        NewFieldManagerBean bean = beanFor(owner, new ArrayList<>());
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        verify(recordingUnitService).addMeasurementField(7L, created);
    }

    @Test
    @DisplayName("A field created from a recording unit's form joins the existing-fields options")
    void saveNewField_addsCreatedFieldToOptions() {
        RecordingUnitDTO owner = new RecordingUnitDTO();
        owner.setId(7L);
        List<CustomFieldMeasurement> options = new ArrayList<>();
        NewFieldManagerBean bean = beanFor(owner, options);
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        assertEquals(List.of(created), options);
    }

    @Test
    @DisplayName("An option already listed is not duplicated")
    void saveNewField_doesNotDuplicateAnExistingOption() {
        RecordingUnitDTO owner = new RecordingUnitDTO();
        owner.setId(7L);
        List<CustomFieldMeasurement> options = new ArrayList<>(List.of(created));
        NewFieldManagerBean bean = beanFor(owner, options);
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        assertEquals(1, options.size());
    }

    @Test
    @DisplayName("Forms of entities that do not own measurement fields only create a shared field")
    void saveNewField_doesNotLinkWhenOwnerIsNotARecordingUnit() {
        NewFieldManagerBean bean = beanFor(new SpecimenDTO(), new ArrayList<>());
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        verifyNoInteractions(recordingUnitService);
    }

    @Test
    @DisplayName("A unit that has not been persisted yet has nothing to attach the field to")
    void saveNewField_doesNotLinkWhenRecordingUnitHasNoId() {
        NewFieldManagerBean bean = beanFor(new RecordingUnitDTO(), new ArrayList<>());
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        verifyNoInteractions(recordingUnitService);
        assertTrue(bean.getAddFieldOptions().contains(created));
    }
}
