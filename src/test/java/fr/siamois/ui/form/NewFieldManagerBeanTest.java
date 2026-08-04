package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.services.form.CustomFieldMeasurementService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewFieldManagerBeanTest {

    @Mock private CustomFieldMeasurementService customFieldMeasurementService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private FormService formService;

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
        return beanFor(owner, options, List.of());
    }

    private NewFieldManagerBean beanFor(AbstractEntityDTO owner,
                                        List<CustomFieldMeasurement> options,
                                        List<UnitDefinitionDTO> unitOptions) {
        return new NewFieldManagerBean(
                customFieldMeasurementService,
                recordingUnitService,
                formService,
                formResponse,
                owner,
                options,
                unitOptions);
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
    @DisplayName("The field is created with the unit picked in the editor")
    void saveNewField_storesThePickedUnitOnTheField() {
        UnitDefinitionDTO metre = UnitDefinitionDTO.builder().id(5L).label("Mètre").symbol("m").build();
        NewFieldManagerBean bean = beanFor(new SpecimenDTO(), new ArrayList<>(), List.of(metre));
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.setUnitId(5L);
        bean.saveNewField();

        ArgumentCaptor<CustomFieldMeasurementDTO> saved = ArgumentCaptor.forClass(CustomFieldMeasurementDTO.class);
        verify(customFieldMeasurementService).save(saved.capture());
        assertEquals(metre, saved.getValue().getUnit());
    }

    @Test
    @DisplayName("The editor opens on the base unit, which is what an untouched menu creates")
    void prepareNewField_preselectsTheBaseUnit() {
        UnitDefinitionDTO centimetre = UnitDefinitionDTO.builder().id(4L).symbol("cm").build();
        UnitDefinitionDTO metre = UnitDefinitionDTO.builder().id(5L).symbol("m").systemBase(true).build();
        NewFieldManagerBean bean = beanFor(new SpecimenDTO(), new ArrayList<>(), List.of(centimetre, metre));
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        ArgumentCaptor<CustomFieldMeasurementDTO> saved = ArgumentCaptor.forClass(CustomFieldMeasurementDTO.class);
        verify(customFieldMeasurementService).save(saved.capture());
        assertEquals(metre, saved.getValue().getUnit());
    }

    /** An instance with no unit seeded at all still has to let a field be created. */
    @Test
    @DisplayName("Nothing to offer leaves the field without a unit")
    void saveNewField_leavesTheFieldWithoutUnitWhenNoneIsOffered() {
        NewFieldManagerBean bean = beanFor(new SpecimenDTO(), new ArrayList<>(), List.of());
        when(customFieldMeasurementService.save(any())).thenReturn(created);

        fillEditor(bean);
        bean.saveNewField();

        ArgumentCaptor<CustomFieldMeasurementDTO> saved = ArgumentCaptor.forClass(CustomFieldMeasurementDTO.class);
        verify(customFieldMeasurementService).save(saved.capture());
        assertNull(saved.getValue().getUnit());
    }

    /**
     * A field attached here never goes through {@code FormService}'s init pass, so its answer would
     * otherwise reach the save path with no unit at all.
     */
    @Test
    @DisplayName("The answer of a field added to the form is initialized with the field's unit")
    void addFieldFromMeasurement_initializesTheAnswerWithItsUnit() {
        NewFieldManagerBean bean = beanFor(new SpecimenDTO(), new ArrayList<>());

        bean.addFieldFromMeasurement(new CustomFormPanelUiDto(), created);

        verify(formService).initializeMeasurement(formResponse.getAnswers().get(created), created);
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
