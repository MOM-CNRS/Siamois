package fr.siamois.domain.models.exporttemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Codec JSON des modèles d'export — la seule définition du format (« mapping v3 », voir
 * {@code docs/mapping-description-langage.md}). Le JSON est déclaratif et peut venir d'une instance
 * centrale : tout ce qui sort de la grammaire est rejeté, y compris les clés inconnues.
 *
 * <p>La forme « fil » est celle du langage partagé avec ShareQ3 : des sources nommées, des connexions
 * de racine (une source primaire alimente une feuille), le schéma cible (colonnes ordonnées, avec leur
 * type) et une liste plate de champs (cible ← source). Le modèle en mémoire ({@link
 * ExportTemplateDefinition}) reste organisé par feuille et colonne ; ce codec fait la traduction :
 * <pre>
 * {
 *   "version": 3, "id": "uuid", "templateVersion": "1.0.0", "name": "…", "fileNamePattern": "OA{oaCode}_{date}",
 *   "sources": {
 *     "UE_1":         {"kind":"ENTITY","entity":"RECORDING_UNIT","types":[Concept]},
 *     "OA_1":         {"kind":"PROJECT"},
 *     "Relations_1":  {"kind":"TECHNICAL","key":"STRATIGRAPHIC_RELATIONSHIP"},
 *     "UE_1_project": {"join":{"topterm":"UE_1","on":{"local_key":"project","foreign_key":"id"}}}
 *   },
 *   "root_connections": [{"target_root":"UE","source_topterm":"UE_1"}],
 *   "sheets": {"UE": {"omitIfEmpty": true, "sortBy": ["type_UE"]}},
 *   "schema_target": {"UE.code_OA_NAT": {"type":"text|number|date"}},
 *   "fields": [
 *     {"target":"UE.code_OA_NAT","source":"UE_1_project","field":Field},
 *     {"target":"UE.nature","source":"UE_1","constant":"Parcelle"},
 *     {"target":"UE.ref","source":"UE_1","concat":{"separator":"_","labelSeparator":": ","parts":[{"label":"…","source":"UE_1","field":Field} | {"literal":"…"}]}}
 *   ]
 * }
 * Concept = {"thesaurus":"th230","id":"4290928","uri":"…"}
 * Field   = {"concept":Concept,"property":"unit|comment"?} | {"column":"nom"} | "nom" (cœur commun)
 * ("property" : propriété imbriquée d'une mesure ; sans elle, la valeur — extension du profil Siamois)
 * </pre>
 * Une source avec {@code join} est parcourue depuis une autre (navigation à cardinalité 1) : c'est le
 * {@code join} de ShareQ3, dont {@code local_key} est ici le nom d'une navigation enregistrée côté
 * code et {@code foreign_key} vaut toujours {@code id}. La nature d'une source jointe se déduit de la
 * navigation. Les clés de sources techniques et de navigations sont vérifiées par
 * {@code ExportTemplateChecker}, pas ici.
 */
public final class ExportTemplateJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SOURCES = "sources";
    private static final String FIELD = "field";
    private static final String THESAURUS = "thesaurus";
    private static final String CONCEPT = "concept";
    private static final String PROPERTY = "property";
    private static final String COLUMN = "column";
    private static final String SEPARATOR = "separator";
    private static final String LABEL = "label";
    private static final String LITERAL = "literal";
    private static final String KIND = "kind";
    private static final String ENTITY = "entity";
    private static final String TYPES = "types";
    private static final String JOIN = "join";
    private static final String TARGET = "target";
    private static final String SOURCE = "source";
    private static final String LABEL_SEPARATOR = "labelSeparator";
    private static final String OMIT_IF_EMPTY = "omitIfEmpty";
    private static final String SORT_BY = "sortBy";
    private static final String FOREIGN_KEY_ID = "id";
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
        Map<String, Object> sources = new LinkedHashMap<>();
        List<Object> roots = new ArrayList<>();
        Map<String, Object> sheetOptions = new LinkedHashMap<>();
        Map<String, Object> schemaTarget = new LinkedHashMap<>();
        List<Object> fields = new ArrayList<>();
        for (Sheet sheet : d.sheets()) {
            requireSheetName(sheet.name());
            SheetWriter writer = new SheetWriter(sheet, sources);
            writer.writeSources(roots);
            if (sheet.omitIfEmpty() || !sheet.sortBy().isEmpty()) {
                Map<String, Object> options = new LinkedHashMap<>();
                if (sheet.omitIfEmpty()) options.put(OMIT_IF_EMPTY, true);
                if (!sheet.sortBy().isEmpty()) options.put(SORT_BY, sheet.sortBy());
                sheetOptions.put(sheet.name(), options);
            }
            for (Column column : sheet.columns()) {
                schemaTarget.put(sheet.name() + "." + column.header(), Map.of("type", column.output().name().toLowerCase(Locale.ROOT)));
                for (Rule rule : column.rules()) {
                    writer.writeFields(sheet.name() + "." + column.header(), rule, fields);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", ExportTemplateDefinition.CURRENT_SCHEMA_VERSION);
        out.put("id", d.id());
        out.put("templateVersion", d.version());
        out.put("name", d.name());
        if (d.fileNamePattern() != null) out.put("fileNamePattern", d.fileNamePattern());
        out.put(SOURCES, sources);
        out.put("root_connections", roots);
        if (!sheetOptions.isEmpty()) out.put("sheets", sheetOptions);
        out.put("schema_target", schemaTarget);
        out.put("fields", fields);
        return out;
    }

    private static void requireSheetName(String name) {
        if (name.contains(".")) {
            throw new InvalidExportTemplateException("A sheet name cannot contain '.': " + name);
        }
    }

    /** Écrit les sources et les champs d'une feuille ; les noms de source sont générés et uniques. */
    private static final class SheetWriter {
        private final Sheet sheet;
        private final Map<String, Object> sources;
        private final List<String> primaryAliases = new ArrayList<>();
        private final Map<String, String> joinedAliases = new HashMap<>();

        SheetWriter(Sheet sheet, Map<String, Object> sources) {
            this.sheet = sheet;
            this.sources = sources;
        }

        void writeSources(List<Object> roots) {
            for (int i = 0; i < sheet.sources().size(); i++) {
                String alias = unique(sheet.name() + "_" + (i + 1));
                sources.put(alias, sourceToWire(sheet.sources().get(i)));
                primaryAliases.add(alias);
                Map<String, Object> root = new LinkedHashMap<>();
                root.put("target_root", sheet.name());
                root.put("source_topterm", alias);
                roots.add(root);
            }
        }

        private String unique(String base) {
            String alias = base;
            int n = 2;
            while (sources.containsKey(alias)) alias = base + "~" + n++;
            return alias;
        }

        /** Source (primaire ou jointe) désignée par un chemin de navigations depuis la source primaire. */
        private String aliasAt(String primary, List<String> path) {
            String current = primary;
            for (String navigation : path) {
                String key = current + "\u0000" + navigation;
                String existing = joinedAliases.get(key);
                if (existing == null) {
                    existing = unique(current + "_" + navigation);
                    Map<String, Object> on = new LinkedHashMap<>();
                    on.put("local_key", navigation);
                    on.put("foreign_key", FOREIGN_KEY_ID);
                    Map<String, Object> join = new LinkedHashMap<>();
                    join.put("topterm", current);
                    join.put("on", on);
                    sources.put(existing, Map.of(JOIN, join));
                    joinedAliases.put(key, existing);
                }
                current = existing;
            }
            return current;
        }

        void writeFields(String target, Rule rule, List<Object> fields) {
            List<Integer> indexes = rule.sources().isEmpty() ? allIndexes(sheet.sources().size()) : rule.sources();
            for (int index : indexes) {
                String primary = primaryAliases.get(index);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put(TARGET, target);
                if (rule instanceof DirectRule r) {
                    entry.put(SOURCE, aliasAt(primary, r.path()));
                    entry.put(FIELD, fieldToWire(r.field()));
                    if (r.list() != null) {
                        entry.put("list", Map.of(SEPARATOR, r.list().separator(), "sorted", r.list().sorted()));
                    }
                } else if (rule instanceof ConstantRule r) {
                    entry.put(SOURCE, primary);
                    entry.put("constant", r.value());
                } else if (rule instanceof ConcatRule r) {
                    entry.put(SOURCE, primary);
                    Map<String, Object> concat = new LinkedHashMap<>();
                    concat.put(SEPARATOR, r.separator());
                    concat.put(LABEL_SEPARATOR, r.labelSeparator());
                    concat.put("parts", r.parts().stream().map(p -> partToWire(p, primary)).toList());
                    entry.put("concat", concat);
                }
                fields.add(entry);
            }
        }

        private Map<String, Object> partToWire(ConcatPart p, String primary) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (p.label() != null) m.put(LABEL, p.label());
            if (p.literal() != null) m.put(LITERAL, p.literal());
            if (p.field() != null) {
                m.put(SOURCE, aliasAt(primary, p.path()));
                m.put(FIELD, fieldToWire(p.field()));
            }
            return m;
        }
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
        } else if (source instanceof TableSource t) {
            m.put(KIND, "TABLE");
            m.put("name", t.name());
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

    private static Object fieldToWire(FieldRef field) {
        if (field instanceof ConceptField c) {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put(CONCEPT, conceptToWire(c.concept()));
            if (c.property() != null) wire.put(PROPERTY, c.property());
            return wire;
        }
        if (field instanceof NameField n) return n.name();
        return Map.of(COLUMN, ((ColumnField) field).name());
    }

    private static List<Integer> allIndexes(int count) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(i);
        return out;
    }

    // ------------------------------------------------------------------ lecture

    public static ExportTemplateDefinition parse(String json) {
        try {
            return fromJson(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new InvalidExportTemplateException("Malformed JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Source déclarée : primaire (lue dans les données) ou jointe (parcourue depuis une autre). */
    private record Declared(@Nullable Source source, @Nullable String joinFrom, @Nullable String joinKey) {
        boolean joined() {
            return joinFrom != null;
        }
    }

    /** Source primaire d'une feuille et chemin de navigations qui y mène depuis elle. */
    private record Resolved(String primary, List<String> path) {
    }

    public static ExportTemplateDefinition fromJson(@Nullable JsonNode root) {
        JsonNode node = requireObject(root, "template");
        only(node, "template", "version", "id", "templateVersion", "name", "fileNamePattern", SOURCES,
                "root_connections", "sheets", "schema_target", "fields");
        int version = requireInt(node, "version");
        if (version != ExportTemplateDefinition.CURRENT_SCHEMA_VERSION) {
            throw new InvalidExportTemplateException("Unsupported version: " + version);
        }
        Map<String, Declared> declared = readSources(required(node, SOURCES));
        Map<String, List<String>> sheetSources = readRootConnections(node, declared);
        addSourcelessSheets(required(node, "schema_target"), sheetSources);
        Map<String, JsonNode> sheetOptions = readSheetOptions(node, sheetSources);
        Map<String, Map<String, OutputType>> columns = readSchemaTarget(required(node, "schema_target"), sheetSources);
        Map<String, Map<String, List<Rule>>> rules = readFields(required(node, "fields"), declared, sheetSources, columns);

        List<Sheet> sheets = new ArrayList<>();
        for (String sheetName : sheetSources.keySet()) {
            if (!columns.containsKey(sheetName)) {
                throw new InvalidExportTemplateException("Sheet '" + sheetName + "' has no column");
            }
        }
        for (Map.Entry<String, Map<String, OutputType>> e : columns.entrySet()) {
            String name = e.getKey();
            Map<String, OutputType> headers = columns.getOrDefault(name, Map.of());
            if (headers.isEmpty()) throw new InvalidExportTemplateException("Sheet '" + name + "' has no column");
            List<Column> sheetColumns = new ArrayList<>();
            headers.forEach((header, output) -> sheetColumns.add(
                    new Column(header, output, mergeRules(rules.getOrDefault(name, Map.of()).getOrDefault(header, List.of())))));
            JsonNode options = sheetOptions.get(name);
            List<String> sortBy = options == null ? List.of() : readList(options, SORT_BY, false, n -> requireString(n, "sortBy entry"));
            for (String h : sortBy) {
                if (!headers.containsKey(h)) {
                    throw new InvalidExportTemplateException("sortBy references unknown header '" + h + "' in sheet " + name);
                }
            }
            boolean omit = options != null && options.has(OMIT_IF_EMPTY) && requireBoolean(options, OMIT_IF_EMPTY);
            List<Source> sources = sheetSources.get(name).stream().map(a -> declared.get(a).source()).toList();
            sheets.add(new Sheet(name, omit, sources, sortBy, sheetColumns));
        }
        return new ExportTemplateDefinition(
                version,
                requireText(node, "id"),
                requireText(node, "templateVersion"),
                requireText(node, "name"),
                optionalText(node, "fileNamePattern"),
                sheets);
    }

    private static Map<String, Declared> readSources(JsonNode node) {
        requireObject(node, SOURCES);
        Map<String, Declared> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> out.put(e.getKey(), declaredFromJson(e.getValue())));
        for (Map.Entry<String, Declared> e : out.entrySet()) {
            checkNoCycle(e.getKey(), out);
        }
        return out;
    }

    private static Declared declaredFromJson(JsonNode node) {
        requireObject(node, "source");
        if (!node.has(JOIN)) {
            return new Declared(sourceFromJson(node), null, null);
        }
        only(node, "source", JOIN);
        JsonNode join = requireObject(node.get(JOIN), JOIN);
        only(join, JOIN, "topterm", "on");
        JsonNode on = requireObject(required(join, "on"), "on");
        only(on, "on", "local_key", "foreign_key");
        String foreign = requireText(on, "foreign_key");
        if (!FOREIGN_KEY_ID.equals(foreign)) {
            throw new InvalidExportTemplateException("foreign_key must be 'id', got '" + foreign + "'");
        }
        return new Declared(null, requireText(join, "topterm"), requireText(on, "local_key"));
    }

    private static void checkNoCycle(String alias, Map<String, Declared> declared) {
        Set<String> seen = new HashSet<>();
        String current = alias;
        while (declared.get(current) != null && declared.get(current).joined()) {
            if (!seen.add(current)) throw new InvalidExportTemplateException("Join cycle on source '" + alias + "'");
            current = declared.get(current).joinFrom();
            if (!declared.containsKey(current)) {
                throw new InvalidExportTemplateException("Join from unknown source '" + current + "'");
            }
        }
    }

    private static Map<String, List<String>> readRootConnections(JsonNode node, Map<String, Declared> declared) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (JsonNode c : readListNodes(node, "root_connections")) {
            requireObject(c, "root connection");
            only(c, "root connection", "target_root", "source_topterm");
            String sheet = requireText(c, "target_root");
            requireSheetName(sheet);
            String alias = requireText(c, "source_topterm");
            Declared d = declared.get(alias);
            if (d == null) throw new InvalidExportTemplateException("Unknown source '" + alias + "'");
            if (d.joined()) throw new InvalidExportTemplateException("Source '" + alias + "' is joined: it cannot feed a sheet");
            List<String> aliases = out.computeIfAbsent(sheet, k -> new ArrayList<>());
            if (aliases.contains(alias)) {
                throw new InvalidExportTemplateException("Source '" + alias + "' feeds sheet '" + sheet + "' twice");
            }
            aliases.add(alias);
        }
        if (out.isEmpty()) throw new InvalidExportTemplateException("'root_connections' cannot be empty");
        return out;
    }

    /**
     * Une feuille déclarée dans {@code schema_target} sans source dans {@code root_connections} est une
     * feuille sans ligne (ses en-têtes seulement) : le modèle la porte pour qu'on la branche plus tard.
     */
    private static void addSourcelessSheets(JsonNode schemaTarget, Map<String, List<String>> sheetSources) {
        requireObject(schemaTarget, "schema_target").fieldNames().forEachRemaining(target -> {
            int dot = target.indexOf('.');
            if (dot > 0) {
                String sheet = target.substring(0, dot);
                requireSheetName(sheet);
                sheetSources.putIfAbsent(sheet, new ArrayList<>());
            }
        });
    }

    private static Map<String, JsonNode> readSheetOptions(JsonNode node, Map<String, List<String>> sheetSources) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (!node.has("sheets")) return out;
        requireObject(node.get("sheets"), "sheets").fields().forEachRemaining(e -> {
            if (!sheetSources.containsKey(e.getKey())) {
                throw new InvalidExportTemplateException("Options for unknown sheet '" + e.getKey() + "'");
            }
            requireObject(e.getValue(), "sheet options");
            only(e.getValue(), "sheet options", OMIT_IF_EMPTY, SORT_BY);
            out.put(e.getKey(), e.getValue());
        });
        return out;
    }

    private static Map<String, Map<String, OutputType>> readSchemaTarget(JsonNode node, Map<String, List<String>> sheetSources) {
        Map<String, Map<String, OutputType>> out = new LinkedHashMap<>();
        requireObject(node, "schema_target").fields().forEachRemaining(e -> {
            String[] target = splitTarget(e.getKey(), sheetSources);
            JsonNode column = requireObject(e.getValue(), "schema_target entry");
            only(column, "schema_target entry", "type");
            OutputType type = column.has("type")
                    ? enumValue(OutputType.class, requireText(column, "type").toUpperCase(Locale.ROOT), "type")
                    : OutputType.TEXT;
            out.computeIfAbsent(target[0], k -> new LinkedHashMap<>()).put(target[1], type);
        });
        return out;
    }

    private static String[] splitTarget(String target, Map<String, List<String>> sheetSources) {
        int dot = target.indexOf('.');
        if (dot <= 0 || dot == target.length() - 1) {
            throw new InvalidExportTemplateException("Invalid target '" + target + "' (expected 'Sheet.Column')");
        }
        String sheet = target.substring(0, dot);
        if (!sheetSources.containsKey(sheet)) {
            throw new InvalidExportTemplateException("Target '" + target + "' refers to an unknown sheet");
        }
        return new String[]{sheet, target.substring(dot + 1)};
    }

    private static Map<String, Map<String, List<Rule>>> readFields(JsonNode node, Map<String, Declared> declared,
                                                                    Map<String, List<String>> sheetSources,
                                                                    Map<String, Map<String, OutputType>> columns) {
        Map<String, Map<String, List<Rule>>> out = new LinkedHashMap<>();
        Set<String> covered = new HashSet<>();
        if (!node.isArray()) throw new InvalidExportTemplateException("'fields' must be an array");
        for (JsonNode f : node) {
            requireObject(f, "field entry");
            only(f, "field entry", TARGET, SOURCE, FIELD, "list", "constant", "concat", "status");
            String target = requireText(f, TARGET);
            String[] t = splitTarget(target, sheetSources);
            if (!columns.getOrDefault(t[0], Map.of()).containsKey(t[1])) {
                throw new InvalidExportTemplateException("Target '" + target + "' is not in schema_target");
            }
            Resolved resolved = resolve(requireText(f, SOURCE), declared);
            int index = sheetSources.get(t[0]).indexOf(resolved.primary());
            if (index < 0) {
                throw new InvalidExportTemplateException("Source '" + resolved.primary() + "' does not feed sheet '" + t[0] + "'");
            }
            if (!covered.add(target + "\u0000" + index)) {
                throw new InvalidExportTemplateException("Several fields target '" + target + "' from the same source");
            }
            out.computeIfAbsent(t[0], k -> new LinkedHashMap<>()).computeIfAbsent(t[1], k -> new ArrayList<>())
                    .add(ruleFromEntry(f, resolved, declared, List.of(index)));
        }
        return out;
    }

    private static Rule ruleFromEntry(JsonNode f, Resolved resolved, Map<String, Declared> declared, List<Integer> sources) {
        boolean hasField = f.has(FIELD);
        boolean hasConstant = f.has("constant");
        boolean hasConcat = f.has("concat");
        if ((hasField ? 1 : 0) + (hasConstant ? 1 : 0) + (hasConcat ? 1 : 0) != 1) {
            throw new InvalidExportTemplateException("A field entry needs exactly one of 'field', 'constant' or 'concat'");
        }
        if (f.has("list") && !hasField) {
            throw new InvalidExportTemplateException("'list' only applies to a 'field' entry");
        }
        if (hasField) {
            return new DirectRule(sources, fieldFromJson(f.get(FIELD)), resolved.path(), listOptions(f.get("list")));
        }
        if (hasConstant) {
            if (!resolved.path().isEmpty()) {
                throw new InvalidExportTemplateException("A constant must use a primary source");
            }
            return new ConstantRule(sources, requireString(f.get("constant"), "constant"));
        }
        if (!resolved.path().isEmpty()) {
            throw new InvalidExportTemplateException("A concat must use a primary source");
        }
        JsonNode concat = requireObject(f.get("concat"), "concat");
        only(concat, "concat", SEPARATOR, LABEL_SEPARATOR, "parts");
        return new ConcatRule(
                sources,
                readList(concat, "parts", true, p -> partFromJson(p, resolved.primary(), declared)),
                concat.has(SEPARATOR) ? requireString(concat.get(SEPARATOR), SEPARATOR) : "",
                concat.has(LABEL_SEPARATOR) ? requireString(concat.get(LABEL_SEPARATOR), LABEL_SEPARATOR) : DEFAULT_LABEL_SEPARATOR);
    }

    /** Remonte les jointures d'une source jusqu'à sa source primaire, en collectant les navigations. */
    private static Resolved resolve(String alias, Map<String, Declared> declared) {
        Declared d = declared.get(alias);
        if (d == null) throw new InvalidExportTemplateException("Unknown source '" + alias + "'");
        LinkedList<String> path = new LinkedList<>();
        String current = alias;
        while (declared.get(current).joined()) {
            path.addFirst(declared.get(current).joinKey());
            current = declared.get(current).joinFrom();
        }
        return new Resolved(current, List.copyOf(path));
    }

    private static Source sourceFromJson(JsonNode node) {
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
            case "TABLE" -> {
                only(node, "source", KIND, "name");
                yield new TableSource(requireText(node, "name"));
            }
            default -> throw new InvalidExportTemplateException("Unknown source kind: " + kind);
        };
    }

    private static ConceptRef conceptFromJson(JsonNode node) {
        requireObject(node, CONCEPT);
        only(node, CONCEPT, THESAURUS, "id", "uri");
        return new ConceptRef(requireText(node, THESAURUS), requireText(node, "id"), optionalText(node, "uri"));
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

    private static ConcatPart partFromJson(JsonNode node, String primary, Map<String, Declared> declared) {
        requireObject(node, "part");
        only(node, "part", LABEL, LITERAL, SOURCE, FIELD);
        boolean hasField = node.has(FIELD);
        boolean hasLiteral = node.has(LITERAL);
        if (hasField == hasLiteral) {
            throw new InvalidExportTemplateException("A concat part needs exactly one of 'field' or 'literal'");
        }
        if (hasLiteral && node.has(SOURCE)) {
            throw new InvalidExportTemplateException("A literal concat part cannot have a source");
        }
        List<String> path = List.of();
        if (hasField) {
            Resolved resolved = resolve(requireText(node, SOURCE), declared);
            if (!resolved.primary().equals(primary)) {
                throw new InvalidExportTemplateException("A concat part must come from the same primary source as its field entry");
            }
            path = resolved.path();
        }
        return new ConcatPart(
                optionalText(node, LABEL),
                hasLiteral ? requireString(node.get(LITERAL), LITERAL) : null,
                hasField ? fieldFromJson(node.get(FIELD)) : null,
                path);
    }

    private static FieldRef fieldFromJson(JsonNode node) {
        if (node.isTextual()) {
            if (node.textValue().isBlank()) throw new InvalidExportTemplateException("A field name cannot be blank");
            return new NameField(node.textValue());
        }
        requireObject(node, FIELD);
        if (node.has(CONCEPT)) {
            only(node, FIELD, CONCEPT, PROPERTY);
            String property = node.has(PROPERTY) ? requireText(node, PROPERTY) : null;
            if (property != null && !ConceptField.MEASUREMENT_PROPERTIES.contains(property)) {
                throw new InvalidExportTemplateException("Unknown field property '" + property + "'");
            }
            return new ConceptField(conceptFromJson(node.get(CONCEPT)), property);
        }
        if (node.has(COLUMN)) {
            only(node, FIELD, COLUMN);
            return new ColumnField(requireText(node, COLUMN));
        }
        throw new InvalidExportTemplateException("A field needs 'concept' or 'column' (or a name)");
    }

    /** Fusionne les règles identiques à leurs sources près (une entrée du fil = une source). */
    private static List<Rule> mergeRules(List<Rule> rules) {
        List<Rule> out = new ArrayList<>();
        for (Rule rule : rules) {
            int same = -1;
            for (int i = 0; i < out.size() && same < 0; i++) {
                if (withSources(out.get(i), List.of()).equals(withSources(rule, List.of()))) same = i;
            }
            if (same < 0) {
                out.add(rule);
            } else {
                List<Integer> merged = new ArrayList<>(out.get(same).sources());
                merged.addAll(rule.sources());
                out.set(same, withSources(out.get(same), merged.stream().sorted().toList()));
            }
        }
        return out;
    }

    private static Rule withSources(Rule rule, List<Integer> sources) {
        if (rule instanceof DirectRule r) return new DirectRule(sources, r.field(), r.path(), r.list());
        if (rule instanceof ConstantRule r) return new ConstantRule(sources, r.value());
        ConcatRule r = (ConcatRule) rule;
        return new ConcatRule(sources, r.parts(), r.separator(), r.labelSeparator());
    }

    // ------------------------------------------------------------------ utilitaires

    private static List<JsonNode> readListNodes(JsonNode parent, String key) {
        return readList(parent, key, true, n -> n);
    }

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
