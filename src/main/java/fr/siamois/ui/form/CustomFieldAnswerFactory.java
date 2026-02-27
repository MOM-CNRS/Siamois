package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.ui.viewmodel.fieldanswer.*;

import java.util.Map;
import java.util.function.Supplier;

public final class CustomFieldAnswerFactory {

    private CustomFieldAnswerFactory() {
    }

    private static final Map<Class<? extends CustomField>, Supplier<? extends CustomFieldAnswerViewModel>> ANSWER_CREATORS =
            Map.ofEntries(
                    Map.entry(CustomFieldText.class, CustomFieldAnswerTextViewModel::new),
                    Map.entry(CustomFieldSelectOneFromFieldCode.class, CustomFieldAnswerSelectOneFromFieldCodeViewModel::new),
                    Map.entry(CustomFieldSelectMultiplePerson.class, CustomFieldAnswerSelectMultiplePersonViewModel::new),
                    Map.entry(CustomFieldDateTime.class, CustomFieldAnswerDateTimeViewModel::new),
                    Map.entry(CustomFieldSelectOneActionUnit.class, CustomFieldAnswerSelectOneActionUnitViewModel::new),
                    Map.entry(CustomFieldSelectOneSpatialUnit.class, CustomFieldAnswerSelectOneSpatialUnitViewModel::new),
                    Map.entry(CustomFieldSelectMultipleSpatialUnitTree.class, CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel::new),
                    Map.entry(CustomFieldSelectOneActionCode.class, CustomFieldAnswerSelectOneActionCodeViewModel::new),
                    Map.entry(CustomFieldInteger.class, CustomFieldAnswerIntegerViewModel::new),
                    Map.entry(CustomFieldSelectOnePerson.class, CustomFieldAnswerSelectOnePersonViewModel::new),
                    Map.entry(CustomFieldStratigraphy.class, CustomFieldAnswerStratigraphyViewModel::new)
            );

    public static CustomFieldAnswerViewModel instantiateAnswerForField(CustomField field) {
        Supplier<? extends CustomFieldAnswerViewModel> creator = ANSWER_CREATORS.get(field.getClass());
        if (creator != null) {
            return creator.get();
        }
        throw new IllegalArgumentException("Unsupported CustomField type: " + field.getClass().getName());
    }
}
