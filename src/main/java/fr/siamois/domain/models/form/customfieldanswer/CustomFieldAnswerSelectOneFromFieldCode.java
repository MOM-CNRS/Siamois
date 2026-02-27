package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;


@Data
@Entity
@SuperBuilder    // <-- indispensable ici aussi
@NoArgsConstructor
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOneFromFieldCode extends CustomFieldAnswer {

    private ConceptDTO value;

    public void setValue(ConceptLabelDTO conceptLabel) {
        this.value = conceptLabel.getConcept();
    }

    public void setValue(ConceptDTO concept) {
        this.value = concept;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOneFromFieldCode that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
