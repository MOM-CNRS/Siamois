package fr.siamois.ui.form;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.form.customform.ValueMatcher;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;

import java.util.Objects;

public final class ValueMatcherFactory {

    private ValueMatcherFactory() {}

    public static ValueMatcher forSelectOneFromFieldCode(EnabledWhenJson.ValueJson vj) {
        JsonNode node = vj.getValue();
        return new ValueMatcher() {
            @Override
            public boolean matches(CustomFieldAnswerViewModel cur) {
                if (!(cur instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel a)) return false;
                String ev = node != null && node.has("vocabularyExtId") ? node.get("vocabularyExtId").asText(null) : null;
                String ec = node != null && node.has("conceptExtId") ? node.get("conceptExtId").asText(null) : null;

                ConceptDTO val = a.getValue().concept();
                if (val == null) {
                    return ev == null && ec == null;
                }
                return Objects.equals(val.getVocabulary().getExternalVocabularyId(), ev)
                        && Objects.equals(val.getExternalId(), ec);
            }

            @Override
            public Class<?> expectedAnswerClass() {
                return CustomFieldAnswerSelectOneFromFieldCode.class;
            }
        };
    }

    public static ValueMatcher defaultMatcher() {
        return new ValueMatcher() {
            @Override
            public boolean matches(CustomFieldAnswerViewModel cur) {
                return false;
            }

            @Override
            public Class<?> expectedAnswerClass() {
                return Object.class;
            }
        };
    }
}

