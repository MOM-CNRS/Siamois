package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ADDRESS")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectOneAddress extends CustomField {

}
