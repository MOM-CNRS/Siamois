package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@DiscriminatorValue("DATETIME")
@Table(name = "custom_field")
public class CustomFieldDateTime extends CustomField {

    private Boolean showTime = false;

    private LocalDateTime min;

    private LocalDateTime max;

    public static class Builder {

        private final CustomFieldDateTime field = new  CustomFieldDateTime();

        public CustomFieldDateTime.Builder label(String label) {
            field.setLabel(label);
            return this;
        }

        public CustomFieldDateTime.Builder isSystemField(Boolean isSystemField) {
            field.setIsSystemField(isSystemField);
            return this;
        }

        public CustomFieldDateTime.Builder concept(Concept concept) {
            field.setConcept(concept);
            return this;
        }

        public CustomFieldDateTime.Builder showTime(Boolean showTime) {
            field.setShowTime(showTime);
            return this;
        }

        public CustomFieldDateTime.Builder valueBinding(String valueBinding) {
            field.setValueBinding(valueBinding);
            return this;
        }

        public CustomFieldDateTime build() {
            return field;
        }
    }

}
