package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;

import java.util.Map;
import java.util.function.Supplier;

public final class CustomFieldAnswerFactory {

    private CustomFieldAnswerFactory() {
    }

    private static final Map<Class<? extends CustomField>, Supplier<? extends CustomFieldAnswer>> ANSWER_CREATORS =
            Map.ofEntries(
                    Map.entry(CustomFieldText.class, CustomFieldAnswerText::new),
                    Map.entry(CustomFieldSelectOneFromFieldCode.class, CustomFieldAnswerSelectOneFromFieldCode::new),
                    Map.entry(CustomFieldSelectMultiplePerson.class, CustomFieldAnswerSelectMultiplePerson::new),
                    Map.entry(CustomFieldDateTime.class, CustomFieldAnswerDateTime::new),
                    Map.entry(CustomFieldSelectOneActionUnit.class, CustomFieldAnswerSelectOneActionUnit::new),
                    Map.entry(CustomFieldSelectOneSpatialUnit.class, CustomFieldAnswerSelectOneSpatialUnit::new),
                    Map.entry(CustomFieldSelectMultipleSpatialUnitTree.class, CustomFieldAnswerSelectMultipleSpatialUnitTree::new),
                    Map.entry(CustomFieldSelectOneActionCode.class, CustomFieldAnswerSelectOneActionCode::new),
                    Map.entry(CustomFieldInteger.class, CustomFieldAnswerInteger::new),
                    Map.entry(CustomFieldSelectOnePerson.class, CustomFieldAnswerSelectOnePerson::new),
                    Map.entry(CustomFieldStratigraphy.class, CustomFieldAnswerStratigraphy::new)
            );

    public static CustomFieldAnswer instantiateAnswerForField(CustomField field) {
        Supplier<? extends CustomFieldAnswer> creator = ANSWER_CREATORS.get(field.getClass());
        if (creator != null) {
            return creator.get();
        }
        throw new IllegalArgumentException("Unsupported CustomField type: " + field.getClass().getName());
    }
}
