package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.container.CustomFieldSelectMultipleContainer;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectOneRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionCode;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectMultiplePerson;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectOnePerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.function.Function;

public final class CustomFieldAnswerFactory {

    private CustomFieldAnswerFactory() {
    }

    /**
     * La Map accepte une Function qui prend le CustomField en entrée.
     */
    private static final Map<Class<? extends CustomField>, Function<CustomField, ? extends CustomFieldAnswerViewModel>> ANSWER_VIEW_CREATORS = initAnswerViewCreators();
    public static final Map<Class<? extends CustomField>, Function<Void, ? extends CustomFieldAnswer>> ANSWER_ENTITY_CREATORS = initAnswerEntityCreators();

    private static @NonNull Map<Class<? extends CustomField>, Function<CustomField, ? extends CustomFieldAnswerViewModel>> initAnswerViewCreators() {
        return Map.ofEntries(
                // Constructeurs vides : on ignore l'argument 'f'
                Map.entry(CustomFieldText.class, f -> new CustomFieldAnswerTextViewModel()),
                Map.entry(CustomFieldSelectOneAddress.class, f -> new CustomFieldAnswerSelectOneAddressViewModel()),
                Map.entry(CustomFieldSelectOneFromFieldCode.class, f -> new CustomFieldAnswerSelectOneFromFieldCodeViewModel()),
                Map.entry(CustomFieldSelectMultiplePerson.class, f -> new CustomFieldAnswerSelectMultiplePersonViewModel()),
                Map.entry(CustomFieldDateTime.class, f -> new CustomFieldAnswerDateTimeViewModel()),
                Map.entry(CustomFieldSelectOneActionUnit.class, f -> new CustomFieldAnswerSelectOneActionUnitViewModel()),
                Map.entry(CustomFieldSelectOneSpatialUnit.class, f -> new CustomFieldAnswerSelectOneSpatialUnitViewModel(
                        ((CustomFieldSelectOneSpatialUnit) f).getSource())),
                Map.entry(CustomFieldSelectMultipleSpatialUnitTree.class, f ->
                        new CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel(((CustomFieldSelectMultipleSpatialUnitTree) f).getSource())),
                Map.entry(CustomFieldSelectOneActionCode.class, f -> new CustomFieldAnswerSelectOneActionCodeViewModel()),
                Map.entry(CustomFieldInteger.class, f -> new CustomFieldAnswerIntegerViewModel()),
                Map.entry(CustomFieldSelectOnePerson.class, f -> new CustomFieldAnswerSelectOnePersonViewModel()),
                Map.entry(CustomFieldSelectMultipleRecordingUnit.class, f -> new CustomFieldAnswerSelectMultipleRecordingUnitViewModel()),
                Map.entry(CustomFieldMeasurement.class, f -> new CustomFieldAnswerMeasurementViewModel()),
                Map.entry(CustomFieldSelectMultipleContainer.class, f -> new CustomFieldAnswerSelectMultipleContainerViewModel()),
                Map.entry(CustomFieldSelectOneRecordingUnit.class, f -> new CustomFieldAnswerSelectOneRecordingUnitViewModel()),
                Map.entry(CustomFieldSelectMultipleSpecimen.class, f -> new CustomFieldAnswerSelectMultipleSpecimenViewModel()),
                Map.entry(CustomFieldSelectMultiplePhase.class, f -> new CustomFieldAnswerSelectMultiplePhaseViewModel()),
                Map.entry(CustomFieldSelectMultipleFromFieldCode.class, f -> new CustomFieldAnswerSelectMultipleFromFieldCodeViewModel())
        );
    }

    private static Map<Class<? extends CustomField>, Function<Void,? extends CustomFieldAnswer>> initAnswerEntityCreators() {
        return Map.ofEntries(
                Map.entry(CustomFieldText.class, v -> new CustomFieldAnswerText()),
                Map.entry(CustomFieldInteger.class, v -> new CustomFieldAnswerInteger()),
                Map.entry(CustomFieldDateTime.class, v -> new CustomFieldAnswerDateTime()),
                Map.entry(CustomFieldSelectOneFromFieldCode.class, v -> new CustomFieldAnswerSelectOneFromFieldCode()),
                Map.entry(CustomFieldSelectMultipleFromFieldCode.class, v -> new CustomFieldAnswerSelectMultiple()),
                Map.entry(CustomFieldSelectOnePerson.class, v -> new CustomFieldAnswerSelectOnePerson()),
                Map.entry(CustomFieldSelectMultiplePerson.class, v -> new CustomFieldAnswerSelectMultiplePerson()),
                Map.entry(CustomFieldSelectOneSpatialUnit.class, v -> new CustomFieldAnswerSelectOneSpatialUnit()),
                Map.entry(CustomFieldSelectMultipleSpatialUnitTree.class, v -> new CustomFieldAnswerSelectMultipleSpatialUnitTree()),
                Map.entry(CustomFieldSelectOneActionCode.class, v -> new CustomFieldAnswerSelectOneActionCode()),
                Map.entry(CustomFieldSelectOneActionUnit.class, v -> new CustomFieldAnswerSelectOneActionUnit())
        );
    }


    public static CustomFieldAnswerViewModel instantiateAnswerForField(CustomField field) {
        if (field == null) return null;

        // Unwrap: a field loaded through a lazy association (e.g. FieldFormConfig#field) is a
        // Hibernate proxy whose getClass() is a generated subclass, missing from this map entirely.
        Class<?> fieldClass = Hibernate.getClass(field);
        Function<CustomField, ? extends CustomFieldAnswerViewModel> creator = ANSWER_VIEW_CREATORS.get(fieldClass);

        if (creator != null) {
            return creator.apply(field);
        }

        throw new IllegalArgumentException("Unsupported CustomField type: " + fieldClass.getName());
    }
}