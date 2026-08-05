package fr.siamois.domain.models.form.customfield.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Entity
@NoArgsConstructor
@SuperBuilder
@Data
public abstract class CustomFieldConceptFromFieldCode extends CustomFieldConcept {

    @Column(name = "field_code")
    protected String fieldCode ;

}
