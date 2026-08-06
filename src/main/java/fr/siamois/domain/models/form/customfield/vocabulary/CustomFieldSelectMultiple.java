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
@DiscriminatorValue("SELECT_MULTIPLE")
@Table(name = "custom_field")
public class CustomFieldSelectMultiple extends CustomFieldConcept {

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
        if (!(o instanceof CustomFieldSelectMultiple that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(concepts, that.concepts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), concepts);
    }


}
