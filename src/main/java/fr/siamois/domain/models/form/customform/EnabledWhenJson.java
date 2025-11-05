package fr.siamois.domain.models.form.customform;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class EnabledWhenJson implements Serializable {
    public enum Op {EQ, NEQ, IN}

    private Op op;                   // "eq" | "neq" | "in"
    private Long fieldId;            // ID du CustomField comparé
    private List<ValueJson> values = new ArrayList<>();

    @Data
    public static class ValueJson implements Serializable {
        /** Classe concrète de la réponse, ex:
         *  "fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger" */
        private String answerClass;

        /** Valeur sérialisée, typée JSON:
         *  - nombre (IntNode/LongNode/DoubleNode)
         *  - objet (ObjectNode), ex { "vocabularyExtId":"th230", "conceptExtId":"1234" } */
        private transient JsonNode value;
    }
}
