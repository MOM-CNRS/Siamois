package fr.siamois.domain.models.form.customfield.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE")
@Table(name = "custom_field")
public class CustomFieldSelectOne extends CustomFieldConcept {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(

            name = "custom_field_choices",
            joinColumns = { @JoinColumn(name = "fk_custom_field") },
            inverseJoinColumns = { @JoinColumn(name = "fk_concept") }

    )
    private Set<Concept> concepts = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldSelectOne that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(concepts, that.concepts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), concepts);
    }

    public static class Builder {

        private final CustomFieldSelectOne field = new  CustomFieldSelectOne();

        public CustomFieldSelectOne.Builder label(String label) {
            field.setLabel(label);
            return this;
        }

        public CustomFieldSelectOne.Builder isSystemField(Boolean isSystemField) {
            field.setIsSystemField(isSystemField);
            return this;
        }

        public CustomFieldSelectOne.Builder concept(Concept concept) {
            field.setConcept(concept);
            return this;
        }

        public CustomFieldSelectOne.Builder valueBinding(String valueBinding) {
            field.setValueBinding(valueBinding);
            return this;
        }

        public CustomFieldSelectOne build() {
            return field;
        }
    }

}
