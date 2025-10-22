package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
@Table(name = "custom_field")
public class CustomFieldSelectOneFromFieldCode extends CustomField {

    @Column(name = "field_code")
    private String fieldCode ;

    @Column(name = "icon_class")
    private String iconClass;

    @Column(name = "style_class")
    private String styleClass;

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public static class Builder {

        private final CustomFieldSelectOneFromFieldCode field = new  CustomFieldSelectOneFromFieldCode();

        public Builder label(String label) {
            field.setLabel(label);
            return this;
        }

        public Builder isSystemField(Boolean isSystemField) {
            field.setIsSystemField(isSystemField);
            return this;
        }

        public Builder concept(Concept concept) {
            field.setConcept(concept);
            return this;
        }

        public Builder valueBinding(String valueBinding) {
            field.setValueBinding(valueBinding);
            return this;
        }

        public Builder fieldCode(String fieldCode) {
            field.setFieldCode(fieldCode);
            return this;
        }

        public Builder iconClass(String iconClass) {
            field.setIconClass(iconClass);
            return this;
        }

        public Builder styleClass(String styleClass) {
            field.setStyleClass(styleClass);
            return this;
        }

        public CustomFieldSelectOneFromFieldCode build() {
            return field;
        }
    }


}
