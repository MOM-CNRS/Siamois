package fr.siamois.domain.services.form.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.ConceptIdLookup;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldRulesJson;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The initial layout of each configurable table, read from {@code form-layouts/<TABLE>.json}.
 * <pre>
 * {GROUPS:[{"label":"…",FIELDS:[{FIELD_ID:-301,"width":"QUARTER","required":true,"rules":{…}}]}]}
 * </pre>
 * Fields are the table's system fields (by their stable id). Rules cite concepts by their thesaurus
 * identifiers ({@code vocabularyExtId}/{@code conceptExtId}), which are portable between instances;
 * they are turned into internal concept ids here. A field whose rules cite a concept this instance
 * does not have keeps no rules (and a warning is logged) rather than a rule that could never match.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormLayoutSeeds {

    private static final String FIELD_ID = "fieldId";

    private static final String FIELDS = "fields";

    private static final String GROUPS = "groups";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConceptIdLookup conceptIds;
    private final Map<ConfigurableTable, JsonNode> raw = new EnumMap<>(ConfigurableTable.class);

    /** The table's initial layout: every field active, mandatory where the seed says so. */
    public FormLayout layoutOf(ConfigurableTable table) {
        Map<Long, CustomField> catalog = SystemFieldCatalog.sharedFieldsOf(table).stream()
                .collect(Collectors.toMap(CustomField::getId, f -> f));
        List<FormLayout.Group> groups = new ArrayList<>();
        for (JsonNode group : rawOf(table).get(GROUPS)) {
            List<FormLayout.Item> items = new ArrayList<>();
            for (JsonNode field : group.get(FIELDS)) {
                long fieldId = field.get(FIELD_ID).asLong();
                CustomField customField = catalog.get(fieldId);
                if (customField == null) {
                    throw new IllegalStateException("form-layouts/" + table + ".json cites field " + fieldId
                            + ", which is not a system field of " + table);
                }
                items.add(new FormLayout.Item(
                        customField,
                        FieldWidth.valueOf(field.path("width").asText(FieldWidth.QUARTER.name())),
                        true,
                        field.path("required").asBoolean(false),
                        false,
                        rulesOf(table, fieldId, field.get("rules"))));
            }
            groups.add(new FormLayout.Group(null, group.get("label").asText(), items));
        }
        return new FormLayout(groups);
    }

    /** The ids of the fields the initial layout marks mandatory — no rule resolution involved. */
    public Set<Long> requiredFieldIds(ConfigurableTable table) {
        Set<Long> ids = new HashSet<>();
        for (JsonNode group : rawOf(table).get(GROUPS)) {
            for (JsonNode field : group.get(FIELDS)) {
                if (field.path("required").asBoolean(false)) ids.add(field.get(FIELD_ID).asLong());
            }
        }
        return ids;
    }

    /** The rules the initial layout gives a field, {@link FieldRules#NONE} when it has none. */
    public FieldRules rulesOf(ConfigurableTable table, long fieldId) {
        for (JsonNode group : rawOf(table).get(GROUPS)) {
            for (JsonNode field : group.get(FIELDS)) {
                if (field.get(FIELD_ID).asLong() == fieldId) return rulesOf(table, fieldId, field.get("rules"));
            }
        }
        return FieldRules.NONE;
    }

    private FieldRules rulesOf(ConfigurableTable table, long fieldId, JsonNode rules) {
        if (rules == null || rules.isNull()) return FieldRules.NONE;
        JsonNode resolved = withInternalConceptIds(rules.deepCopy());
        if (resolved == null) {
            log.warn("Rules of field {} in form-layouts/{}.json cite a concept missing from this instance; ignored",
                    fieldId, table);
            return FieldRules.NONE;
        }
        return FieldRulesJson.fromJson(resolved);
    }

    /** Replaces every thesaurus-identified concept by its internal id; null if one is unknown. */
    private JsonNode withInternalConceptIds(JsonNode node) {
        if (node.isObject()) {
            if (node.has("vocabularyExtId") && node.has("conceptExtId")) {
                Optional<Long> id = conceptIds.conceptId(node.get("vocabularyExtId").asText(), node.get("conceptExtId").asText());
                if (id.isEmpty()) return null;
                ObjectNode out = MAPPER.createObjectNode();
                out.put("conceptId", String.valueOf(id.get()));
                return out;
            }
            ObjectNode object = (ObjectNode) node;
            for (var it = object.fields(); it.hasNext(); ) {
                var entry = it.next();
                JsonNode replaced = withInternalConceptIds(entry.getValue());
                if (replaced == null) return null;
                entry.setValue(replaced);
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode replaced = withInternalConceptIds(array.get(i));
                if (replaced == null) return null;
                array.set(i, replaced);
            }
        }
        return node;
    }

    private synchronized JsonNode rawOf(ConfigurableTable table) {
        return raw.computeIfAbsent(table, t -> {
            String path = "/form-layouts/" + t.name() + ".json";
            try (InputStream in = FormLayoutSeeds.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Missing " + path);
                return MAPPER.readTree(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
