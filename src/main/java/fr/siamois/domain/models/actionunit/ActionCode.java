package fr.siamois.domain.models.actionunit;


import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "action_code")
@Audited
public class ActionCode implements Serializable {

    @Id
    @Column(name = "action_code_id", nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_type", nullable = false)
    protected Concept type;

    @FieldCode
    public static final String TYPE_FIELD_CODE = "SIAAC.TYPE";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionCode actionCode)) return false;
        return code.equals(actionCode.code) &&  type.equals(actionCode.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, type);
    }
}
