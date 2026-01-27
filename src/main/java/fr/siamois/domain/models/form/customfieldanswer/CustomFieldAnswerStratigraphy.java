package fr.siamois.domain.models.form.customfieldanswer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Data
@Entity
@DiscriminatorValue("STRATIGRAPHY")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerStratigraphy extends CustomFieldAnswer {

    private transient Set<StratigraphicRelationship> anteriorRelationships = new HashSet<>();
    private transient Set<StratigraphicRelationship> posteriorRelationships = new HashSet<>();
    private transient Set<StratigraphicRelationship> synchronousRelationships = new HashSet<>();

    private transient ConceptAutocompleteDTO conceptToAdd;
    private transient RecordingUnit sourceToAdd = new RecordingUnit(); // always the recording unit the panel is about
    private transient RecordingUnit targetToAdd;
    private transient Boolean vocabularyDirectionToAdd = false; // always false in this version
    private transient Boolean isUncertainToAdd;

    public String getRelationshipsAsJson() {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // anterior
        ArrayNode anteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationship rel : anteriorRelationships) {
            ObjectNode node = mapper.createObjectNode();
            node.put("unit1Id", rel.getUnit1().getFullIdentifier());
            node.put("vocabularyLabel", rel.getConcept().getExternalId());
            node.put("vocabularyDirection", rel.getConceptDirection());
            node.put("uncertain", rel.getUncertain() != null && rel.getUncertain());
            anteriorArray.add(node);
        }
        root.set("anterior", anteriorArray);

        // posterior
        ArrayNode posteriorArray = mapper.createArrayNode();
        for (StratigraphicRelationship rel : posteriorRelationships) {
            ObjectNode node = mapper.createObjectNode();
            node.put("unit1Id", rel.getUnit2().getFullIdentifier());
            node.put("vocabularyLabel", rel.getConcept().getExternalId());
            node.put("vocabularyDirection", rel.getConceptDirection());
            node.put("uncertain", rel.getUncertain() != null && rel.getUncertain());
            posteriorArray.add(node);
        }
        root.set("posterior", posteriorArray);

        // synchronous
        ArrayNode synchronousArray = mapper.createArrayNode();
        for (StratigraphicRelationship rel : synchronousRelationships) {
            ObjectNode node = mapper.createObjectNode();
            node.put("unit1Id", rel.getUnit2().getFullIdentifier());
            node.put("vocabularyLabel", rel.getConcept().getExternalId());
            node.put("vocabularyDirection", rel.getConceptDirection());
            node.put("uncertain", rel.getUncertain() != null && rel.getUncertain());
            synchronousArray.add(node);
        }
        root.set("synchronous", synchronousArray);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerStratigraphy that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
