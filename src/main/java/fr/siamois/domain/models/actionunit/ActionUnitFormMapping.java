package fr.siamois.domain.models.actionunit;


import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "action_unit_form_mapping")
@Audited
public class ActionUnitFormMapping implements Serializable {

    @EmbeddedId
    private ActionUnitFormMappingId pk;

}
