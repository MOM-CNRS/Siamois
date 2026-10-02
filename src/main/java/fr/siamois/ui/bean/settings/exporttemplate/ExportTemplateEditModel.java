package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Copie modifiable d'un {@link ExportTemplateDefinition} pour les formulaires de l'éditeur : les
 * records de la définition sont immuables, et un formulaire a besoin de setters et de valeurs
 * textuelles. {@link #from} et {@link #toDefinition} font l'aller-retour sans perte.
 *
 * <p>Un champ est désigné dans le formulaire par une clé texte : {@code "thesaurus|id|uri"} pour un
 * concept, le nom de la colonne pour une colonne de table technique (voir {@link EditField}).
 */
@Getter
@Setter
public class ExportTemplateEditModel implements Serializable {

    public static final String KIND_ENTITY = "ENTITY";
    public static final String KIND_PROJECT = "PROJECT";
    public static final String KIND_TECHNICAL = "TECHNICAL";
    public static final String RULE_DIRECT = "DIRECT";
    public static final String RULE_CONSTANT = "CONSTANT";
    public static final String RULE_CONCAT = "CONCAT";
    public static final String FIELD_CONCEPT = "CONCEPT";
    public static final String FIELD_COLUMN = "COLUMN";

    private static final String KEY_SEPARATOR = "|";
    private static final String DEFAULT_LIST_SEPARATOR = " & ";
    private static final String DEFAULT_LABEL_SEPARATOR = ": ";

    private int schemaVersion = ExportTemplateDefinition.CURRENT_SCHEMA_VERSION;
    private String id;
    private String version;
    private String name;
    private String fileNamePattern;
    private List<EditSheet> sheets = new ArrayList<>();

    @Getter
    @Setter
    public static class EditSheet implements Serializable {
        private String name;
        private boolean omitIfEmpty;
        private List<EditSource> sources = new ArrayList<>();
        private List<String> sortBy = new ArrayList<>();
        private List<EditColumn> columns = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class EditSource implements Serializable {
        private String kind = KIND_PROJECT;
        private String entity;
        private String technicalKey;
        /** Types (concepts) sous forme de clés {@code "thesaurus|id|uri"}. */
        private List<String> typeKeys = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class EditColumn implements Serializable {
        private String header;
        private String output = OutputType.TEXT.name();
        private List<EditRule> rules = new ArrayList<>();
    }

    /** Champ édité : un concept ({@code key = "thesaurus|id|uri"}) ou une colonne technique ({@code key = nom}). */
    @Getter
    @Setter
    public static class EditField implements Serializable {
        private String kind = FIELD_CONCEPT;
        private String key;
    }

    @Getter
    @Setter
    public static class EditRule implements Serializable {
        private String type = RULE_DIRECT;
        /** Indices de sources ciblés, en texte pour les cases à cocher ; vide = toutes. */
        private List<String> sources = new ArrayList<>();
        private EditField field = new EditField();
        /** Navigations séparées par des virgules. */
        private String path = "";
        private boolean list;
        private String listSeparator = DEFAULT_LIST_SEPARATOR;
        private boolean listSorted;
        private String constantValue = "";
        private List<EditPart> parts = new ArrayList<>();
        private String separator = "";
        private String labelSeparator = DEFAULT_LABEL_SEPARATOR;
    }

    @Getter
    @Setter
    public static class EditPart implements Serializable {
        private String label;
        /** Vrai pour un texte littéral ({@code field} est alors ignoré), même si le texte saisi est vide. */
        private boolean literalPart;
        private String literal;
        private EditField field = new EditField();
        private String path = "";

    }

    // ------------------------------------------------------------------ définition -> formulaire

    public static ExportTemplateEditModel from(ExportTemplateDefinition d) {
        ExportTemplateEditModel m = new ExportTemplateEditModel();
        m.schemaVersion = d.schemaVersion();
        m.id = d.id();
        m.version = d.version();
        m.name = d.name();
        m.fileNamePattern = d.fileNamePattern();
        d.sheets().forEach(s -> m.sheets.add(sheetFrom(s)));
        return m;
    }

    private static EditSheet sheetFrom(Sheet s) {
        EditSheet e = new EditSheet();
        e.name = s.name();
        e.omitIfEmpty = s.omitIfEmpty();
        e.sortBy = new ArrayList<>(s.sortBy());
        s.sources().forEach(src -> e.sources.add(sourceFrom(src)));
        s.columns().forEach(c -> e.columns.add(columnFrom(c)));
        return e;
    }

    private static EditSource sourceFrom(Source source) {
        EditSource e = new EditSource();
        if (source instanceof EntitySource s) {
            e.kind = KIND_ENTITY;
            e.entity = s.entity().name();
            s.types().forEach(t -> e.typeKeys.add(keyOf(t)));
        } else if (source instanceof TechnicalSource s) {
            e.kind = KIND_TECHNICAL;
            e.technicalKey = s.key();
        } else {
            e.kind = KIND_PROJECT;
        }
        return e;
    }

    private static EditColumn columnFrom(Column c) {
        EditColumn e = new EditColumn();
        e.header = c.header();
        e.output = c.output().name();
        c.rules().forEach(r -> e.rules.add(ruleFrom(r)));
        return e;
    }

    private static EditRule ruleFrom(Rule rule) {
        EditRule e = new EditRule();
        rule.sources().forEach(i -> e.sources.add(String.valueOf(i)));
        if (rule instanceof DirectRule r) {
            e.type = RULE_DIRECT;
            e.field = fieldFrom(r.field());
            e.path = String.join(",", r.path());
            if (r.list() != null) {
                e.list = true;
                e.listSeparator = r.list().separator();
                e.listSorted = r.list().sorted();
            }
        } else if (rule instanceof ConstantRule r) {
            e.type = RULE_CONSTANT;
            e.constantValue = r.value();
        } else if (rule instanceof ConcatRule r) {
            e.type = RULE_CONCAT;
            e.separator = r.separator();
            e.labelSeparator = r.labelSeparator();
            r.parts().forEach(p -> e.parts.add(partFrom(p)));
        }
        return e;
    }

    private static EditPart partFrom(ConcatPart p) {
        EditPart e = new EditPart();
        e.label = p.label();
        e.literalPart = p.literal() != null;
        e.literal = p.literal();
        if (p.field() != null) e.field = fieldFrom(p.field());
        e.path = String.join(",", p.path());
        return e;
    }

    private static EditField fieldFrom(FieldRef f) {
        EditField e = new EditField();
        if (f instanceof ConceptField c) {
            e.kind = FIELD_CONCEPT;
            e.key = keyOf(c.concept());
        } else {
            e.kind = FIELD_COLUMN;
            e.key = ((ColumnField) f).name();
        }
        return e;
    }

    // ------------------------------------------------------------------ formulaire -> définition

    public ExportTemplateDefinition toDefinition() {
        return new ExportTemplateDefinition(schemaVersion, id, version, name, blankToNull(fileNamePattern),
                sheets.stream().map(ExportTemplateEditModel::sheetTo).toList());
    }

    private static Sheet sheetTo(EditSheet s) {
        return new Sheet(s.name, s.omitIfEmpty, s.sources.stream().map(ExportTemplateEditModel::sourceTo).toList(),
                List.copyOf(s.sortBy), s.columns.stream().map(ExportTemplateEditModel::columnTo).toList());
    }

    private static Source sourceTo(EditSource s) {
        return switch (s.kind) {
            case KIND_ENTITY -> {
                if (s.entity == null || s.entity.isBlank()) {
                    throw new IllegalArgumentException("An entity source needs an entity");
                }
                yield new EntitySource(EntityKind.valueOf(s.entity),
                        s.typeKeys.stream().map(ExportTemplateEditModel::refOf).toList());
            }
            case KIND_TECHNICAL -> new TechnicalSource(s.technicalKey);
            default -> new ProjectSource();
        };
    }

    private static Column columnTo(EditColumn c) {
        return new Column(c.header, OutputType.valueOf(c.output), c.rules.stream().map(ExportTemplateEditModel::ruleTo).toList());
    }

    private static Rule ruleTo(EditRule r) {
        List<Integer> sources = r.sources.stream().map(Integer::valueOf).distinct().sorted().toList();
        return switch (r.type) {
            case RULE_CONSTANT -> new ConstantRule(sources, r.constantValue == null ? "" : r.constantValue);
            case RULE_CONCAT -> new ConcatRule(sources, r.parts.stream().map(ExportTemplateEditModel::partTo).toList(),
                    r.separator == null ? "" : r.separator,
                    r.labelSeparator == null ? DEFAULT_LABEL_SEPARATOR : r.labelSeparator);
            default -> new DirectRule(sources, fieldTo(r.field), pathOf(r.path),
                    r.list ? new ListOptions(r.listSeparator == null ? DEFAULT_LIST_SEPARATOR : r.listSeparator, r.listSorted) : null);
        };
    }

    private static ConcatPart partTo(EditPart p) {
        if (p.isLiteralPart()) {
            return new ConcatPart(blankToNull(p.label), p.literal == null ? "" : p.literal, null, List.of());
        }
        return new ConcatPart(blankToNull(p.label), null, fieldTo(p.field), pathOf(p.path));
    }

    private static FieldRef fieldTo(EditField f) {
        if (FIELD_COLUMN.equals(f.kind)) {
            return new ColumnField(f.key);
        }
        return new ConceptField(refOf(f.key));
    }

    // ------------------------------------------------------------------ copie

    /** Copie profonde d'une règle, pour l'éditer dans le tiroir sans toucher au modèle avant « Appliquer ». */
    public static EditRule copyOf(EditRule rule) {
        EditRule c = new EditRule();
        c.setType(rule.getType());
        c.setSources(new ArrayList<>(rule.getSources()));
        c.setField(copyOf(rule.getField()));
        c.setPath(rule.getPath());
        c.setList(rule.isList());
        c.setListSeparator(rule.getListSeparator());
        c.setListSorted(rule.isListSorted());
        c.setConstantValue(rule.getConstantValue());
        c.setSeparator(rule.getSeparator());
        c.setLabelSeparator(rule.getLabelSeparator());
        for (EditPart p : rule.getParts()) {
            EditPart pc = new EditPart();
            pc.setLabel(p.getLabel());
            pc.setLiteralPart(p.isLiteralPart());
            pc.setLiteral(p.getLiteral());
            pc.setField(copyOf(p.getField()));
            pc.setPath(p.getPath());
            c.getParts().add(pc);
        }
        return c;
    }

    private static EditField copyOf(EditField f) {
        EditField c = new EditField();
        c.setKind(f.getKind());
        c.setKey(f.getKey());
        return c;
    }

    // ------------------------------------------------------------------ clés et chemins

    /** {@code "thesaurus|id|uri"} (uri vide si absente). */
    public static String keyOf(ConceptRef ref) {
        return ref.thesaurusId() + KEY_SEPARATOR + ref.conceptId() + KEY_SEPARATOR + (ref.uri() == null ? "" : ref.uri());
    }

    public static ConceptRef refOf(String key) {
        String[] parts = key == null ? new String[0] : key.split("\\|", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid concept key: " + key);
        }
        return new ConceptRef(parts[0], parts[1], parts.length > 2 && !parts[2].isBlank() ? parts[2] : null);
    }

    /** Navigations d'un chemin saisi à la main, séparées par des virgules. */
    public static List<String> pathOf(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(path.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
