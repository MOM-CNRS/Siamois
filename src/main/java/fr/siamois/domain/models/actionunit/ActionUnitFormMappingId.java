package fr.siamois.domain.models.actionunit;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@Audited
public class ActionUnitFormMappingId implements Serializable {

    @ManyToOne
    @JoinColumn(name = "fk_action_unit", nullable = false)
    private ActionUnit actionUnit;

    @ManyToOne
    @JoinColumn(name = "fk_custom_form", nullable = false)
    private CustomForm form;

    @ManyToOne
    @JoinColumn(name = "fk_concept", nullable = false)
    private Concept concept;

    @Column(name= "table_name", nullable = false)
    private String tableName;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ActionUnitFormMappingId that = (ActionUnitFormMappingId) o;
        return Objects.equals(getActionUnit(), that.getActionUnit()) && Objects.equals(getForm(), that.getForm()) && Objects.equals(getConcept(), that.getConcept()) && Objects.equals(getTableName(), that.getTableName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getActionUnit(), getForm(), getConcept(), getTableName());
    }
}
