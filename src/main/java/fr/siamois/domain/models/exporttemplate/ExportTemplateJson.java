package fr.siamois.domain.models.exporttemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Codec JSON des modèles d'export — la seule définition du format. Le JSON est déclaratif et peut
 * venir d'une instance centrale : tout ce qui sort de la grammaire est rejeté, y compris les clés
 * inconnues.
 * <pre>
 * {
 *   "schemaVersion": 1, "id": "uuid", "version": "1.0.0", "name": "…", "fileNamePattern": "OA{oaCode}_{date}",
 *   "sheets": [{
 *     "name": "UE", "omitIfEmpty": true, "sortBy": ["type_UE"],
 *     "sources": [Source],
 *     "columns": [{"header": "code_OA_NAT", "output": "TEXT|NUMBER|DATE", "rules": [Rule]}]
 *   }]
 * }
 * Source  = {"kind":"ENTITY","entity":"RECORDING_UNIT","types":[Concept]} | {"kind":"PROJECT"} | {"kind":"TECHNICAL","key":"…"}
 * Concept = {"thesaurus":"th230","id":"4290928","uri":"…"}
 * Field   = {"concept":Concept} | {"column":"nom"}
 * Rule    = {"type":"DIRECT","sources":[0],"field":Field,"path":["project"],"list":{"separator":" &amp; ","sorted":true}}
 *         | {"type":"CONSTANT","sources":[0],"value":"…"}
 *         | {"type":"CONCAT","sources":[0],"separator":"_","labelSeparator":": ","parts":[{"label":"…","field":Field,"path":[…]} | {"literal":"…"}]}
 * </pre>
 * Les noms de la liste {@code path} et les clés de sources techniques sont des clés enregistrées côté
 * code ; leur existence est vérifiée au moment de l'export, pas ici.
 */
public final class ExportTemplateJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SOURCES = "sources";
    private static final String FIELD = "field";
    private static final String PATH = "path";
    private static final String THESAURUS = "thesaurus";
    private static final String CONCEPT = "concept";
    private static final String COLUMN = "column";
    private static final String SEPARATOR = "separator";
    private static final String LABEL = "label";
    private static final String LITERAL = "literal";
    private static final String KIND = "kind";
    private static final String HEADER = "header";
    private static final String ENTITY = "entity";
    private static final String TYPES = "types";
    private static final String VALUE = "value";
    private static final String DEFAULT_LABEL_SEPARATOR = ": ";

    private ExportTemplateJson() {
        throw new UnsupportedOperationException();
    }

    // ------------------------------------------------------------------ écriture

    public static String toJson(ExportTemplateDefinition definition) {
        try {
            return MAPPER.writeValueAsString(toWire(definition));
        } catch (JsonProcessingException e) {
            throw new InvalidExportTemplateException("Cannot serialize export template", e);
        }
    }

    /** Forme « fil » en maps/listes sérialisables par Jackson. */
    public static Map<String, Object> toWire(ExportTemplateDefinition d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", d.schemaVersion());
        out.put("id", d.id());
        out.put("version", d.version());
        out.put("name", d.name());
        if (d.fileNamePattern() != null) out.put("fileNamePattern", d.fileNamePattern());
        out.put("sheets", d.sheets().stream().map(ExportTemplateJson::sheetToWire).toList());
        return out;
    }

    private static Map<String, Object> sheetToWire(Sheet s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        if (s.omitIfEmpty()) m.put("omitIfEmpty", true);
        if (!s.sortBy().isEmpty()) m.put("sortBy", s.sortBy());
        m.put(SOURCES, s.sources().stream().map(ExportTemplateJson::sourceToWire).toList());
        m.put("columns", s.columns().stream().map(ExportTemplateJson::columnToWire).toList());
        return m;
    }

    private static Map<String, Object> sourceToWire(Source source) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (source instanceof EntitySource e) {
            m.put(KIND, "ENTITY");
            m.put(ENTITY, e.entity().name());
            if (!e.types().isEmpty()) m.put(TYPES, e.types().stream().map(ExportTemplateJson::conceptToWire).toList());
        } else if (source instanceof TechnicalSource t) {
            m.put(KIND, "TECHNICAL");
            m.put("key", t.key());
        } else {
            m.put(KIND, "PROJECT");
        }
        return m;
    }

    private static Map<String, Object> conceptToWire(ConceptRef c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(THESAURUS, c.thesaurusId());
        m.put("id", c.conceptId());
        if (c.uri() != null) m.put("uri", c.uri());
        return m;
    }

    private static Map<String, Object> columnToWire(Column c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(HEADER, c.header());
        m.put("output", c.output().name());
        m.put("rules", c.rules().stream().map(ExportTemplateJson::ruleToWire).toList());
        return m;
    }

    private static Map<String, Object> ruleToWire(Rule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (rule instanceof DirectRule r) {
            m.put("type", "DIRECT");
            putSources(m, r.sources());
            m.put(FIELD, fieldToWire(r.field()));
            if (!r.path().isEmpty()) m.put(PATH, r.path());
            if (r.list() != null) {
                m.put("list", Map.of(SEPARATOR, r.list().separator(), "sorted", r.list().sorted()));
            }
        } else if (rule instanceof ConstantRule r) {
            m.put("type", "CONSTANT");
            putSources(m, r.sources());
            m.put(VALUE, r.value());
        } else if (rule instanceof ConcatRule r) {
            m.put("type", "CONCAT");
            putSources(m, r.sources());
            m.put(SEPARATOR, r.separator());
            m.put("labelSeparator", r.labelSeparator());
            m.put("parts", r.parts().stream().map(ExportTemplateJson::partToWire).toList());
        }
        return m;
    }

    private static void putSources(Map<String, Object> m, List<Integer> sources) {
        if (!sources.isEmpty()) m.put(SOURCES, sources);
    }

    private static Map<String, Object> partToWire(ConcatPart p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p.label() != null) m.put(LABEL, p.label());
        if (p.literal() != null) m.put(LITERAL, p.literal());
        if (p.field() != null) m.put(FIELD, fieldToWire(p.field()));
        if (!p.path().isEmpty()) m.put(PATH, p.path());
        return m;
    }

    private static Map<String, Object> fieldToWire(FieldRef field) {
        if (field instanceof ConceptField c) return Map.of(CONCEPT, conceptToWire(c.concept()));
        return Map.of(COLUMN, ((ColumnField) field).name());
    }

    // ------------------------------------------------------------------ lecture

    public static ExportTemplateDefinition parse(String json) {
        try {
            return fromJson(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new InvalidExportTemplateException("Malformed JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static ExportTemplateDefinition fromJson(@Nullable JsonNode root) {
        JsonNode node = requireObject(root, "template");
        only(node, "template", "schemaVersion", "id", "version", "name", "fileNamePattern", "sheets");
        int schemaVersion = requireInt(node, "schemaVersion");
        if (schemaVersion != ExportTemplateDefinition.CURRENT_SCHEMA_VERSION) {
            throw new InvalidExportTemplateException("Unsupported schemaVersion: " + schemaVersion);
        }
        List<Sheet> sheets = readList(node, "sheets", true, ExportTemplateJson::sheetFromJson);
        Set<String> names = new HashSet<>();
        for (Sheet s : sheets) {
            if (!names.add(s.name())) throw new InvalidExportTemplateException("Duplicate sheet name: " + s.name());
        }
        return new ExportTemplateDefinition(
                schemaVersion,
                requireText(node, "id"),
                requireText(node, "version"),
                requireText(node, "name"),
                optionalText(node, "fileNamePattern"),
                sheets);
    }

    private static Sheet sheetFromJson(JsonNode node) {
        only(node, "sheet", "name", "omitIfEmpty", "sortBy", SOURCES, "columns");
        String name = requireText(node, "name");
        List<Source> sources = readList(node, SOURCES, true, ExportTemplateJson::sourceFromJson);
        List<Column> columns = readList(node, "columns", true, c -> columnFromJson(c, sources.size()));
        Set<String> headers = new HashSet<>();
        for (Column c : columns) {
            if (!headers.add(c.header())) {
                throw new InvalidExportTemplateException("Duplicate header '" + c.header() + "' in sheet " + name);
            }
        }
        List<String> sortBy = readList(node, "sortBy", false, n -> requireString(n, "sortBy entry"));
        for (String h : sortBy) {
            if (!headers.contains(h)) {
                throw new InvalidExportTemplateException("sortBy references unknown header '" + h + "' in sheet " + name);
            }
        }
        boolean omit = node.has("omitIfEmpty") && requireBoolean(node, "omitIfEmpty");
        return new Sheet(name, omit, sources, sortBy, columns);
    }

    private static Source sourceFromJson(JsonNode node) {
        requireObject(node, "source");
        String kind = requireText(node, KIND);
        return switch (kind) {
            case "ENTITY" -> {
                only(node, "source", KIND, ENTITY, TYPES);
                yield new EntitySource(
                        enumValue(EntityKind.class, requireText(node, ENTITY), ENTITY),
                        readList(node, TYPES, false, ExportTemplateJson::conceptFromJson));
            }
            case "PROJECT" -> {
                only(node, "source", KIND);
                yield new ProjectSource();
            }
            case "TECHNICAL" -> {
                only(node, "source", KIND, "key");
                yield new TechnicalSource(requireText(node, "key"));
            }
            default -> throw new InvalidExportTemplateException("Unknown source kind: " + kind);
        };
    }

    private static ConceptRef conceptFromJson(JsonNode node) {
        requireObject(node, CONCEPT);
        only(node, CONCEPT, THESAURUS, "id", "uri");
        return new ConceptRef(requireText(node, THESAURUS), requireText(node, "id"), optionalText(node, "uri"));
    }

    private static Column columnFromJson(JsonNode node, int sourceCount) {
        requireObject(node, "column");
        only(node, "column", HEADER, "output", "rules");
        String header = requireText(node, HEADER);
        OutputType output = node.has("output")
                ? enumValue(OutputType.class, requireText(node, "output"), "output")
                : OutputType.TEXT;
        List<Rule> rules = readList(node, "rules", true, r -> ruleFromJson(r, sourceCount));
        Set<Integer> covered = new HashSet<>();
        for (Rule r : rules) {
            List<Integer> targets = r.sources().isEmpty() ? allIndexes(sourceCount) : r.sources();
            for (Integer idx : targets) {
                if (!covered.add(idx)) {
                    throw new InvalidExportTemplateException(
                            "Source " + idx + " is targeted by several rules in column " + header);
                }
            }
        }
        return new Column(header, output, rules);
    }

    private static List<Integer> allIndexes(int count) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(i);
        return out;
    }

    private static Rule ruleFromJson(JsonNode node, int sourceCount) {
        requireObject(node, "rule");
        String type = requireText(node, "type");
        List<Integer> sources = readList(node, SOURCES, false, n -> sourceIndex(n, sourceCount));
        return switch (type) {
            case "DIRECT" -> {
                only(node, "rule", "type", SOURCES, FIELD, PATH, "list");
                yield new DirectRule(sources, fieldFromJson(required(node, FIELD)), readPath(node), listOptions(node.get("list")));
            }
            case "CONSTANT" -> {
                only(node, "rule", "type", SOURCES, VALUE);
                yield new ConstantRule(sources, requireString(required(node, VALUE), VALUE));
            }
            case "CONCAT" -> {
                only(node, "rule", "type", SOURCES, SEPARATOR, "labelSeparator", "parts");
                yield new ConcatRule(
                        sources,
                        readList(node, "parts", true, ExportTemplateJson::partFromJson),
                        node.has(SEPARATOR) ? requireString(node.get(SEPARATOR), SEPARATOR) : "",
                        node.has("labelSeparator")
                                ? requireString(node.get("labelSeparator"), "labelSeparator")
                                : DEFAULT_LABEL_SEPARATOR);
            }
            default -> throw new InvalidExportTemplateException("Unknown rule type: " + type);
        };
    }

    private static int sourceIndex(JsonNode n, int sourceCount) {
        if (!n.isInt() || n.intValue() < 0 || n.intValue() >= sourceCount) {
            throw new InvalidExportTemplateException("Invalid source index: " + n);
        }
        return n.intValue();
    }

    @Nullable
    private static ListOptions listOptions(@Nullable JsonNode node) {
        if (node == null || node.isNull()) return null;
        requireObject(node, "list");
        only(node, "list", SEPARATOR, "sorted");
        return new ListOptions(
                requireString(required(node, SEPARATOR), SEPARATOR),
                node.has("sorted") && requireBoolean(node, "sorted"));
    }

    private static ConcatPart partFromJson(JsonNode node) {
        requireObject(node, "part");
        only(node, "part", LABEL, LITERAL, FIELD, PATH);
        boolean hasField = node.has(FIELD);
        boolean hasLiteral = node.has(LITERAL);
        if (hasField == hasLiteral) {
            throw new InvalidExportTemplateException("A concat part needs exactly one of 'field' or 'literal'");
        }
        if (hasLiteral && node.has(PATH)) {
            throw new InvalidExportTemplateException("A literal concat part cannot have a path");
        }
        return new ConcatPart(
                optionalText(node, LABEL),
                hasLiteral ? requireString(node.get(LITERAL), LITERAL) : null,
                hasField ? fieldFromJson(node.get(FIELD)) : null,
                readPath(node));
    }

    private static FieldRef fieldFromJson(JsonNode node) {
        requireObject(node, FIELD);
        if (node.has(CONCEPT)) {
            only(node, FIELD, CONCEPT);
            return new ConceptField(conceptFromJson(node.get(CONCEPT)));
        }
        if (node.has(COLUMN)) {
            only(node, FIELD, COLUMN);
            return new ColumnField(requireText(node, COLUMN));
        }
        throw new InvalidExportTemplateException("A field needs 'concept' or 'column'");
    }

    private static List<String> readPath(JsonNode node) {
        return readList(node, PATH, false, n -> {
            String s = requireString(n, "path entry");
            if (s.isBlank()) throw new InvalidExportTemplateException("Blank path entry");
            return s;
        });
    }

    // ------------------------------------------------------------------ utilitaires

    private static <T> List<T> readList(JsonNode parent, String key, boolean nonEmpty, java.util.function.Function<JsonNode, T> reader) {
        JsonNode array = parent.get(key);
        if (array == null || array.isNull()) {
            if (nonEmpty) throw new InvalidExportTemplateException("Missing '" + key + "'");
            return List.of();
        }
        if (!array.isArray()) throw new InvalidExportTemplateException("'" + key + "' must be an array");
        if (nonEmpty && array.isEmpty()) throw new InvalidExportTemplateException("'" + key + "' cannot be empty");
        List<T> out = new ArrayList<>();
        for (JsonNode n : array) out.add(reader.apply(n));
        return out;
    }

    private static void only(JsonNode node, String what, String... allowed) {
        Set<String> ok = Set.of(allowed);
        node.fieldNames().forEachRemaining(name -> {
            if (!ok.contains(name)) throw new InvalidExportTemplateException("Unknown property '" + name + "' in " + what);
        });
    }

    private static JsonNode requireObject(@Nullable JsonNode node, String what) {
        if (node == null || !node.isObject()) throw new InvalidExportTemplateException("Expected an object for " + what);
        return node;
    }

    private static JsonNode required(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) throw new InvalidExportTemplateException("Missing '" + key + "'");
        return v;
    }

    private static String requireString(JsonNode node, String what) {
        if (!node.isTextual()) throw new InvalidExportTemplateException("Expected a string for " + what);
        return node.textValue();
    }

    private static String requireText(JsonNode node, String key) {
        String s = requireString(required(node, key), key);
        if (s.isBlank()) throw new InvalidExportTemplateException("'" + key + "' cannot be blank");
        return s;
    }

    @Nullable
    private static String optionalText(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? null : requireString(v, key);
    }

    private static int requireInt(JsonNode node, String key) {
        JsonNode v = required(node, key);
        if (!v.isInt()) throw new InvalidExportTemplateException("Expected an integer for " + key);
        return v.intValue();
    }

    private static boolean requireBoolean(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || !v.isBoolean()) throw new InvalidExportTemplateException("Expected a boolean for " + key);
        return v.booleanValue();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new InvalidExportTemplateException("Unknown " + what + ": " + value, e);
        }
    }
}
