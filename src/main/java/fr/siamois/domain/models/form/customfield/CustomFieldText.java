package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;


@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@DiscriminatorValue("TEXT")
@Table(name = "custom_field")
public class CustomFieldText extends CustomField {

    @Column(name = "is_text_area")
    private Boolean isTextArea;

    private transient Supplier<String> autoGenerationFunction;

    public String generateAutoValue() {
        return autoGenerationFunction != null ? autoGenerationFunction.get() : null;
    }// function to autogenerate the value

    public static class Builder {

        private String label; // label or label code if system field
        private Boolean isSystemField;
        private Concept concept;
        private String valueBinding ;
        private Supplier<String> autoGenerationFunction;

        public Builder autoGenerationFunction(Supplier<String> autoGenerationFunction) {
            this.autoGenerationFunction = autoGenerationFunction;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder isSystemField(Boolean isSystemField) {
            this.isSystemField = isSystemField;
            return this;
        }

        public Builder valueBinding(String valueBinding) {
            this.valueBinding = valueBinding;
            return this;
        }

        public Builder concept(Concept concept) {
            this.concept = concept;
            return this;
        }

        public CustomFieldText build() {
            CustomFieldText field = new CustomFieldText();
            field.setIsSystemField(this.isSystemField);
            field.setLabel(this.label);
            field.setValueBinding(this.valueBinding);
            field.setConcept(this.concept);
            field.setAutoGenerationFunction(this.autoGenerationFunction);
            return field;
        }
    }

}
