package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE_CONCEPT_FROM_CHILDREN_OF_CONCEPT")
@Table(name = "custom_field")
public class CustomFieldSelectOneConceptFromChildrenOfConcept extends CustomField {

    @Transient
    private CustomField parentField ; // field that has the parent answer in the form

    @Column(name = "icon_class")
    private String iconClass;

    @Column(name = "style_class")
    private String styleClass;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldSelectOneConceptFromChildrenOfConcept that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(parentField, that.parentField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), parentField);
    }

    public static class Builder {

        private final CustomFieldSelectOneConceptFromChildrenOfConcept field = new  CustomFieldSelectOneConceptFromChildrenOfConcept();

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder label(String label) {
            field.setLabel(label);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder isSystemField(Boolean isSystemField) {
            field.setIsSystemField(isSystemField);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder concept(Concept concept) {
            field.setConcept(concept);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder valueBinding(String valueBinding) {
            field.setValueBinding(valueBinding);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder parentField(CustomField parentField) {
            field.setParentField(parentField);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder iconClass(String iconClass) {
            field.setIconClass(iconClass);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept.Builder styleClass(String styleClass) {
            field.setStyleClass(styleClass);
            return this;
        }

        public CustomFieldSelectOneConceptFromChildrenOfConcept build() {
            return field;
        }
    }


}
