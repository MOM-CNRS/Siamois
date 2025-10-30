package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE_PERSON")
@Table(name = "custom_field")
public class CustomFieldSelectOnePerson extends CustomFieldSelectPerson {

    public static class Builder {

        private final CustomFieldSelectOnePerson field = new  CustomFieldSelectOnePerson();

        public CustomFieldSelectOnePerson.Builder label(String label) {
            field.setLabel(label);
            return this;
        }

        public CustomFieldSelectOnePerson.Builder isSystemField(Boolean isSystemField) {
            field.setIsSystemField(isSystemField);
            return this;
        }

        public CustomFieldSelectOnePerson.Builder concept(Concept concept) {
            field.setConcept(concept);
            return this;
        }

        public CustomFieldSelectOnePerson.Builder valueBinding(String valueBinding) {
            field.setValueBinding(valueBinding);
            return this;
        }

        public CustomFieldSelectOnePerson build() {
            return field;
        }
    }


}
