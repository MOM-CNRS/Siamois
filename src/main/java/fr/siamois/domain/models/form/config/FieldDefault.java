package fr.siamois.domain.models.form.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The value a new entity starts with for a field, as one form configuration sets it. Like the units, it
 * belongs to the field's configuration for a (project, table, type), not to the field itself.
 * <p>
 * For now a default is a list of concepts (one for a single choice, several for a multiple one). A concept
 * is stored by its internal id, as in the field rules, with no foreign key: the list it came from can change
 * afterwards, so whoever applies a default checks it is still offered.
 *
 * @param concepts the concepts a new answer starts with, without duplicates
 */
public record FieldDefault(List<ConceptRef> concepts) implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A concept of a thesaurus, by its internal id. */
    public record ConceptRef(String conceptId) implements Serializable {
    }

    public FieldDefault {
        Map<String, ConceptRef> unique = new LinkedHashMap<>();
        if (concepts != null) {
            concepts.stream().filter(c -> c != null && c.conceptId() != null && !c.conceptId().isBlank())
                    .forEach(c -> unique.putIfAbsent(c.conceptId(), c));
        }
        concepts = List.copyOf(unique.values());
    }

    public static FieldDefault ofConceptIds(List<String> ids) {
        return new FieldDefault(ids == null ? List.of() : ids.stream().map(ConceptRef::new).toList());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return concepts.isEmpty();
    }

    @JsonIgnore
    public List<String> conceptIds() {
        return concepts.stream().map(ConceptRef::conceptId).toList();
    }

    /** The JSON a form configuration stores, or null for no default. */
    @Nullable
    public String toJson() {
        if (isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Default value cannot be written", e);
        }
    }

    /** The default a form configuration stored; null when it has none or it cannot be read. */
    @Nullable
    public static FieldDefault fromJson(@Nullable String json) {
        if (json == null || json.isBlank()) return null;
        try {
            FieldDefault value = MAPPER.readValue(json, FieldDefault.class);
            return value.isEmpty() ? null : value;
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }
}
