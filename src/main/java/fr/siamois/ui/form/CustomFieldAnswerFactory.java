package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.ui.viewmodel.fieldanswer.*;

import java.util.Map;
import java.util.function.Function;

public final class CustomFieldAnswerFactory {

    private CustomFieldAnswerFactory() {
    }

    /**
     * La Map accepte une Function qui prend le CustomField en entrée.
     */
    private static final Map<Class<? extends CustomField>, Function<CustomField, ? extends CustomFieldAnswerViewModel>> ANSWER_CREATORS =
            Map.ofEntries(
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
                    Map.entry(CustomFieldStratigraphy.class, f -> new CustomFieldAnswerStratigraphyViewModel())
            );

    public static CustomFieldAnswerViewModel instantiateAnswerForField(CustomField field) {
        if (field == null) return null;

        Function<CustomField, ? extends CustomFieldAnswerViewModel> creator = ANSWER_CREATORS.get(field.getClass());

        if (creator != null) {
            return creator.apply(field);
        }

        throw new IllegalArgumentException("Unsupported CustomField type: " + field.getClass().getName());
    }
}