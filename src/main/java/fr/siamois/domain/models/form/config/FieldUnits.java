package fr.siamois.domain.models.form.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The units a measurement field is entered in, as one form configuration sets them: the units the
 * user can pick among, and the one a new answer starts in. This belongs to the field's configuration
 * for a (project, table, type), not to the field itself, so two projects can offer different units
 * for the same field.
 * <p>
 * A unit is stored as its catalogue id (what identifies it: a symbol can be shared by units of
 * different dimensions) next to its symbol, which is what the answers and the API carry: the clients
 * need no lookup, and the id is what settles any ambiguity.
 *
 * @param defaultUnitId the unit a new answer starts in; always one of {@code units}
 * @param units         the units an answer can be entered in, all measuring the same thing
 */
public record FieldUnits(Long defaultUnitId, List<UnitRef> units) implements Serializable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A unit of the catalogue: its id, and its symbol at the time it was chosen. */
    public record UnitRef(Long id, String symbol) implements Serializable {
    }

    public FieldUnits {
        Map<Long, UnitRef> unique = new LinkedHashMap<>();
        if (units != null) units.stream().filter(u -> u != null && u.id() != null).forEach(u -> unique.putIfAbsent(u.id(), u));
        units = List.copyOf(new ArrayList<>(unique.values()));
        if (defaultUnitId == null || !unique.containsKey(defaultUnitId)) {
            defaultUnitId = units.stream().map(UnitRef::id).findFirst().orElse(null);
        }
    }

    @JsonIgnore
    public boolean isEmpty() {
        return units.isEmpty();
    }

    @JsonIgnore
    public List<Long> ids() {
        return units.stream().map(UnitRef::id).toList();
    }

    @JsonIgnore
    public List<String> symbols() {
        return units.stream().map(UnitRef::symbol).toList();
    }

    @JsonIgnore
    @Nullable
    public String defaultSymbol() {
        return units.stream().filter(u -> u.id().equals(defaultUnitId)).map(UnitRef::symbol).findFirst().orElse(null);
    }

    /** The JSON a form configuration stores, or null for no units. */
    @Nullable
    public String toJson() {
        if (isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Units cannot be written", e);
        }
    }

    /** The units a form configuration stored; null when it has none or they cannot be read. */
    @Nullable
    public static FieldUnits fromJson(@Nullable String json) {
        if (json == null || json.isBlank()) return null;
        try {
            FieldUnits units = MAPPER.readValue(json, FieldUnits.class);
            return units.isEmpty() ? null : units;
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }
}
