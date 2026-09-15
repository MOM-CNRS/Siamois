package fr.siamois.domain.models.form.customfield.vocabulary;

import fr.siamois.domain.models.form.customfield.CustomField;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public abstract class CustomFieldConcept extends CustomField {

    @Override
    public String getIcon() {
        return "sia-icon-opentheso";
    }

    // Covers CustomFieldSelectOne/Multiple directly, and CustomFieldSelectOne/MultipleFromFieldCode
    // via CustomFieldConceptFromFieldCode — all four share the same edit widget.
    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/concept.xhtml";
    }

}
