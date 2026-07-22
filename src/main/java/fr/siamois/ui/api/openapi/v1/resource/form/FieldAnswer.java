package fr.siamois.ui.api.openapi.v1.resource.form;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "answerType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextFieldAnswer.class, names = {"TEXT"}),
        @JsonSubTypes.Type(value = IntegerFieldAnswer.class, names = {"INTEGER"}),
        @JsonSubTypes.Type(value = DateFieldAnswer.class, names = {"DATETIME"}),
        @JsonSubTypes.Type(value = SelectOneFieldAnswer.class, names = {
                "SELECT_ONE_FROM_FIELD_CODE", "SELECT_ONE_PERSON", "SELECT_ONE_ACTION_UNIT",
                "SELECT_ONE_SPATIAL_UNIT", "SELECT_ONE_ACTION_CODE", "SELECT_ONE_RECORDING_UNIT",
                "SELECT_ADDRESS", "SELECT_ONE"
        }),
        @JsonSubTypes.Type(value = SelectManyFieldAnswer.class, names = {
                "SELECT_MULTIPLE_PERSON", "SELECT_MULTIPLE_FROM_FIELD_CODE",
                "SELECT_MULTIPLE_RECORDING_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
                "SELECT_MULTIPLE_SPECIMEN", "SELECT_MULTIPLE_CONTAINER",
                "SELECT_MULTIPLE_PHASE", "SELECT_MULTIPLE"
        }),
        @JsonSubTypes.Type(value = MeasurementFieldAnswer.class, names = {"MEASUREMENT"})
})
@Schema(
        description = "Valeur d'un champ de formulaire avec sa définition embarquée. "
                + "Le champ answerType (discriminant) détermine le type concret et le schéma de value/values.",
        oneOf = {
                TextFieldAnswer.class,
                IntegerFieldAnswer.class,
                DateFieldAnswer.class,
                SelectOneFieldAnswer.class,
                SelectManyFieldAnswer.class,
                MeasurementFieldAnswer.class
        },
        discriminatorProperty = "answerType"
)
public sealed interface FieldAnswer
        permits TextFieldAnswer, IntegerFieldAnswer, DateFieldAnswer,
                SelectOneFieldAnswer, SelectManyFieldAnswer,
                MeasurementFieldAnswer {

    String answerType();

    FieldResource field();
}
