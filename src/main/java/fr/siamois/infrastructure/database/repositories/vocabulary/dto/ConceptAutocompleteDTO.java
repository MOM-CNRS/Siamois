package fr.siamois.infrastructure.database.repositories.vocabulary.dto;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@Getter
@Builder(access = AccessLevel.PUBLIC)
public class ConceptAutocompleteDTO implements Serializable {

    private final ConceptLabel conceptLabelToDisplay;
    private final String originalPrefLabel;
    private final List<String> altLabels;
    private final String definition;
    private final String hierarchyPrefLabels;

    public ConceptAutocompleteDTO(
            ConceptLabel conceptLabelToDisplay,
            String originalPrefLabel,
            List<String> altLabels,
            String definition,
            String hierarchyPrefLabels) {
        this.conceptLabelToDisplay = conceptLabelToDisplay;
        this.originalPrefLabel = originalPrefLabel;
        this.altLabels = altLabels;
        this.definition = definition;
        this.hierarchyPrefLabels = hierarchyPrefLabels;
    }

    public ConceptAutocompleteDTO(Concept c, String prefLabel, String lang) {
        this.conceptLabelToDisplay = new ConceptPrefLabel();
        this.conceptLabelToDisplay.setConcept(c);
        this.conceptLabelToDisplay.setLabel(prefLabel);
        this.conceptLabelToDisplay.setLangCode(lang);
        this.originalPrefLabel = prefLabel;
        this.altLabels = List.of();
        this.definition = null;
        this.hierarchyPrefLabels = null;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (conceptLabelToDisplay.isAltLabel()) {
            builder.append("AltLabel: ");
        } else {
            builder.append("PrefLabel: ");
        }
        builder.append(conceptLabelToDisplay.getLabel());
        builder.append("(").append(conceptLabelToDisplay.getLangCode()).append(")");
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptAutocompleteDTO that)) return false;
        return Objects.equals(definition, that.definition)
                && Objects.equals(hierarchyPrefLabels, that.hierarchyPrefLabels)
                && Objects.equals(conceptLabelToDisplay, that.conceptLabelToDisplay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conceptLabelToDisplay, definition, hierarchyPrefLabels);
    }

    @NonNull
    public String altlabelsAsString() {
        return String.join(",", altLabels);
    }

    @Nullable
    public Vocabulary vocabulary() {
        return conceptLabelToDisplay.getConcept().getVocabulary();
    }

    @Nullable
    public Concept concept() {
        return conceptLabelToDisplay.getConcept();
    }

}
