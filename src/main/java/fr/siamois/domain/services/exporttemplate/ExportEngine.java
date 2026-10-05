package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Column;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConcatPart;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConcatRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConstantRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.DirectRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntitySource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.FieldRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ListOptions;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.OutputType;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Rule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Sheet;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Source;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.TechnicalSource;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Produit le classeur {@code .xlsx} d'un modèle d'export pour un projet : lit les lignes de chaque source
 * de chaque feuille, évalue les règles de chaque colonne (champ, constante, concaténation), met les valeurs
 * en forme selon le type de sortie, trie, puis écrit le fichier. Aucune formule n'est écrite.
 *
 * <p>Le modèle doit avoir passé {@link ExportTemplateChecker} : le moteur suppose les clés de sources et de
 * navigations valides. Les droits de l'appelant sont vérifiés par {@link ExportService}. Un champ qui ne se
 * résout pas dans le projet laisse sa colonne vide et produit un {@link ExportWarning}, jamais une erreur.
 */
@Service
@RequiredArgsConstructor
public class ExportEngine {

    private static final int PREVIEW_CELL_LENGTH = 300;
    private static final int MAX_SIGNIFICANT_DIGITS = 15;
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+([.,]\\d+)?");
    private static final String DEFAULT_LIST_SEPARATOR = " & ";

    /** @param content le classeur ; {@code rowsBySheet} compte les lignes de données de chaque feuille écrite */
    public record Result(byte[] content, String fileName, List<ExportWarning> warnings, Map<String, Integer> rowsBySheet) {

        @Override
        public boolean equals(Object o) {
            return o instanceof Result r && Arrays.equals(content, r.content) && fileName.equals(r.fileName)
                    && warnings.equals(r.warnings) && rowsBySheet.equals(r.rowsBySheet);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(content), fileName, warnings, rowsBySheet);
        }

        @Override
        public String toString() {
            return "Result[fileName=" + fileName + ", bytes=" + content.length + ", warnings=" + warnings
                    + ", rowsBySheet=" + rowsBySheet + "]";
        }
    }

    private final ExportSourceReader sourceReader;
    private final ExportValueReader valueReader;
    private final ExportFieldResolver fieldResolver;
    private final ConceptLabelBatchResolver labelResolver;
    private final ConceptMapper conceptMapper;

    // ------------------------------------------------------------------ valeurs brutes (avant mise en forme)

    private sealed interface Raw permits RawText, RawValues, RawConcat {
    }

    private record RawText(String text) implements Raw {
        static final RawText EMPTY = new RawText("");
    }

    private record RawValues(List<Object> values, @Nullable ListOptions list) implements Raw {
    }

    private record RawPart(@Nullable String label, @Nullable String literal, List<Object> values) {
    }

    private record RawConcat(List<RawPart> parts, String separator, String labelSeparator) implements Raw {
    }

    /** {@code total} : nombre de lignes lues dans les sources, avant limitation éventuelle (aperçu). */
    private record RawSheet(Sheet sheet, List<List<Raw>> rows, int total) {
    }

    // ------------------------------------------------------------------ export

    @Transactional(readOnly = true)
    public Result run(ExportTemplateDefinition definition, Long projectId, String lang) {
        List<ExportWarning> warnings = new ArrayList<>();
        ActionUnit project = sourceReader.project(projectId);
        List<ExportSheetData> sheets = build(definition, projectId, lang, Integer.MAX_VALUE, warnings);
        List<ExportSheetData> written = sheets.stream().filter(s -> !(s.sheet().omitIfEmpty() && s.rows().isEmpty())).toList();
        if (written.isEmpty()) {
            warnings.add(new ExportWarning(ExportWarning.Code.EMPTY_WORKBOOK, null, null, null));
            written = List.of(sheets.get(0));
        }
        byte[] content = ExportWorkbookWriter.write(written, warnings);
        Map<String, Integer> counts = new LinkedHashMap<>();
        written.forEach(s -> counts.put(s.sheet().name(), s.rows().size()));
        return new Result(content, ExportFileName.of(definition, project, warnings), dedupe(warnings), counts);
    }

    /** Une feuille de l'aperçu : en-têtes, premières lignes en texte, et nombre total de lignes lues. */
    public record SheetPreview(String name, List<String> headers, List<List<String>> rows, int totalRows) {
    }

    public record Preview(List<SheetPreview> sheets, List<ExportWarning> warnings) {
    }

    /**
     * Aperçu d'un modèle sur un projet : les {@code maxRows} premières lignes de chaque source, mises en
     * forme comme dans le fichier, sans l'écrire. Le tri ne porte que sur ces lignes.
     */
    @Transactional(readOnly = true)
    public Preview preview(ExportTemplateDefinition definition, Long projectId, String lang, int maxRows) {
        List<ExportWarning> warnings = new ArrayList<>();
        List<SheetPreview> sheets = new ArrayList<>();
        for (ExportSheetData sheet : build(definition, projectId, lang, maxRows, warnings)) {
            List<String> headers = sheet.sheet().columns().stream().map(Column::header).toList();
            List<List<String>> rows = sheet.rows().stream()
                    .map(r -> r.stream().map(ExportEngine::previewText).toList())
                    .toList();
            sheets.add(new SheetPreview(sheet.sheet().name(), headers, rows, sheet.total()));
        }
        return new Preview(sheets, dedupe(warnings));
    }

    private static String previewText(Object cell) {
        String text = cell instanceof Double d ? new BigDecimal(d.toString()).stripTrailingZeros().toPlainString() : (String) cell;
        return text.length() > PREVIEW_CELL_LENGTH ? text.substring(0, PREVIEW_CELL_LENGTH) + "…" : text;
    }

    /** Lit, évalue et met en forme toutes les feuilles ; {@code limit} borne les lignes lues par source. */
    private List<ExportSheetData> build(ExportTemplateDefinition definition, Long projectId, String lang, int limit,
                                       List<ExportWarning> warnings) {
        List<RawSheet> collected = definition.sheets().stream()
                .map(sheet -> collectRows(sheet, projectId, warnings, limit))
                .toList();
        ExportValueFormatter formatter = new ExportValueFormatter(resolveLabels(collected, lang));
        return collected.stream().map(raw -> format(raw, formatter, warnings)).toList();
    }

    // ------------------------------------------------------------------ lecture des lignes

    private RawSheet collectRows(Sheet sheet, Long projectId, List<ExportWarning> warnings, int limit) {
        List<List<Raw>> rows = new ArrayList<>();
        int total = 0;
        for (int index = 0; index < sheet.sources().size(); index++) {
            Source source = sheet.sources().get(index);
            List<ExportRow> allRows = sourceReader.read(source, projectId);
            total += allRows.size();
            List<ExportRow> sourceRows = allRows.size() > limit ? allRows.subList(0, limit) : allRows;
            int sourceIndex = index;
            List<List<Raw>> perColumn = sheet.columns().stream()
                    .map(column -> evaluate(sheet, column, ruleFor(column, sourceIndex), source, sourceRows, projectId, warnings))
                    .toList();
            for (int r = 0; r < sourceRows.size(); r++) {
                int rowIndex = r;
                rows.add(perColumn.stream().map(columnValues -> columnValues.get(rowIndex)).toList());
            }
        }
        return new RawSheet(sheet, rows, total);
    }

    /** Règle d'une colonne pour la source d'indice donné : la première qui la vise (sans {@code sources} : toutes). */
    private static Optional<Rule> ruleFor(Column column, int sourceIndex) {
        return column.rules().stream()
                .filter(r -> r.sources().isEmpty() || r.sources().contains(sourceIndex))
                .findFirst();
    }

    private List<Raw> evaluate(Sheet sheet, Column column, Optional<Rule> rule, Source source, List<ExportRow> rows,
                               Long projectId, List<ExportWarning> warnings) {
        if (rule.isEmpty()) {
            return rows.stream().map(r -> (Raw) RawText.EMPTY).toList();
        }
        Where where = new Where(sheet.name(), column.header(), source, rows, projectId, warnings);
        if (rule.get() instanceof ConstantRule constant) {
            return rows.stream().map(r -> (Raw) new RawText(constant.value())).toList();
        }
        if (rule.get() instanceof DirectRule direct) {
            List<List<Object>> values = fieldValues(where, direct.field(), direct.path());
            return values.stream().map(v -> (Raw) new RawValues(v, direct.list())).toList();
        }
        ConcatRule concat = (ConcatRule) rule.get();
        List<List<List<Object>>> partValues = concat.parts().stream()
                .map(part -> part.field() == null ? null : fieldValues(where, part.field(), part.path()))
                .toList();
        List<Raw> out = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<RawPart> parts = new ArrayList<>();
            for (int p = 0; p < concat.parts().size(); p++) {
                ConcatPart part = concat.parts().get(p);
                parts.add(new RawPart(part.label(), part.literal(), partValues.get(p) == null ? List.of() : partValues.get(p).get(r)));
            }
            out.add(new RawConcat(parts, concat.separator(), concat.labelSeparator()));
        }
        return out;
    }

    /** Contexte d'évaluation d'une colonne sur une source (pour les avertissements et la résolution). */
    private record Where(String sheet, String column, Source source, List<ExportRow> rows, Long projectId,
                         List<ExportWarning> warnings) {
    }

    /** Valeurs d'un champ pour chaque ligne de la source, après suivi du chemin. */
    private List<List<Object>> fieldValues(Where where, FieldRef field, List<String> path) {
        List<Optional<ExportRow>> targets = where.rows().stream().map(r -> ExportNavigations.follow(r, path)).toList();
        List<CustomField> candidates = List.of();
        ExportValueReader.AnswerCache cache = ExportValueReader.AnswerCache.EMPTY;
        if (field instanceof ConceptField conceptField) {
            ExportSubject subject = subjectAfter(where.source(), path);
            List<ExportTemplateDefinition.ConceptRef> types =
                    path.isEmpty() && where.source() instanceof EntitySource e ? e.types() : List.of();
            candidates = fieldResolver.resolve(subject, conceptField.concept(), where.projectId(), types);
            if (candidates.isEmpty()) {
                where.warnings().add(new ExportWarning(ExportWarning.Code.FIELD_UNRESOLVED, where.sheet(), where.column(),
                        conceptField.concept().thesaurusId() + "|" + conceptField.concept().conceptId()));
                return targets.stream().map(t -> List.<Object>of()).toList();
            }
            cache = valueReader.prefetch(subject, targets.stream().flatMap(Optional::stream).toList(), candidates);
        }
        List<CustomField> resolved = candidates;
        ExportValueReader.AnswerCache answers = cache;
        List<List<Object>> out = targets.stream()
                .map(target -> target.map(t -> valueReader.read(t, field, resolved, answers)).orElseGet(List::of))
                .toList();
        if (field instanceof ConceptField conceptField && conceptField.property() != null) {
            return out.stream().map(values -> property(where, conceptField, values)).toList();
        }
        return out;
    }

    /** Propriété imbriquée d'une mesure ; une valeur qui n'est pas une mesure est écartée avec un avertissement. */
    private static List<Object> property(Where where, ConceptField field, List<Object> values) {
        List<Object> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof MeasurementAnswer m) {
                Object picked = "unit".equals(field.property()) ? unitLabel(m) : m.getComment();
                if (picked != null) out.add(picked);
            } else if (value != null) {
                where.warnings().add(new ExportWarning(ExportWarning.Code.UNSUPPORTED_VALUE, where.sheet(), where.column(), field.property()));
            }
        }
        return out;
    }

    private static String unitLabel(MeasurementAnswer m) {
        return m.getUnit() == null ? null : m.getUnit().getLabel();
    }

    private static ExportSubject subjectAfter(Source source, List<String> path) {
        if (source instanceof TechnicalSource technical) {
            ExportTechnicalSource key = ExportTechnicalSource.ofKey(technical.key())
                    .orElseThrow(() -> new InvalidExportTemplateException("Unknown technical source: " + technical.key()));
            return ExportNavigations.targetOf(key, path);
        }
        return ExportNavigations.targetOf(ExportTemplateChecker.subjectOf(source), path);
    }

    // ------------------------------------------------------------------ libellés de concepts

    private Map<Long, String> resolveLabels(List<RawSheet> sheets, String lang) {
        Map<Long, ConceptDTO> concepts = new LinkedHashMap<>();
        for (RawSheet sheet : sheets) {
            for (List<Raw> row : sheet.rows()) {
                for (Raw raw : row) {
                    rawValues(raw).forEach(v -> {
                        if (v instanceof Concept c && c.getId() != null) {
                            concepts.computeIfAbsent(c.getId(), id -> conceptMapper.convert(c));
                        }
                    });
                }
            }
        }
        return concepts.isEmpty() ? Map.of() : labelResolver.resolveLabels(concepts.values(), lang);
    }

    private static List<Object> rawValues(Raw raw) {
        if (raw instanceof RawValues v) return v.values();
        if (raw instanceof RawConcat c) return c.parts().stream().flatMap(p -> p.values().stream()).toList();
        return List.of();
    }

    // ------------------------------------------------------------------ mise en forme et tri

    private ExportSheetData format(RawSheet raw, ExportValueFormatter formatter, List<ExportWarning> warnings) {
        Sheet sheet = raw.sheet();
        List<List<Object>> rows = new ArrayList<>();
        for (List<Raw> rawRow : raw.rows()) {
            List<Object> row = new ArrayList<>();
            for (int c = 0; c < sheet.columns().size(); c++) {
                row.add(cell(sheet, sheet.columns().get(c), rawRow.get(c), formatter, warnings));
            }
            rows.add(row);
        }
        sort(sheet, rows);
        return new ExportSheetData(sheet, rows, raw.total());
    }

    /**
     * Cellule finale : un texte, ou un nombre pour une colonne de type NUMBER dont la valeur en est un
     * (chiffres avec un séparateur décimal {@code .} ou {@code ,}, au plus 15 chiffres significatifs :
     * au-delà un {@code Double} perdrait de la précision). Sinon le texte est gardé, avec un avertissement.
     */
    private Object cell(Sheet sheet, Column column, Raw raw, ExportValueFormatter formatter, List<ExportWarning> warnings) {
        String text = text(sheet, column, raw, formatter, warnings);
        if (column.output() != OutputType.NUMBER || text.isBlank()) {
            return text;
        }
        String trimmed = text.trim();
        if (NUMBER.matcher(trimmed).matches() && significantDigits(trimmed) <= MAX_SIGNIFICANT_DIGITS) {
            return Double.valueOf(trimmed.replace(',', '.'));
        }
        warnings.add(new ExportWarning(ExportWarning.Code.NOT_NUMERIC, sheet.name(), column.header(), text));
        return text;
    }

    private static int significantDigits(String number) {
        String digits = number.replaceAll("\\D", "").replaceAll("^0+", "");
        return digits.length();
    }

    private String text(Sheet sheet, Column column, Raw raw, ExportValueFormatter formatter, List<ExportWarning> warnings) {
        if (raw instanceof RawText t) {
            return t.text();
        }
        if (raw instanceof RawValues v) {
            return join(strings(v.values(), column.output(), formatter, sheet, column, warnings), v.list());
        }
        RawConcat concat = (RawConcat) raw;
        List<String> pieces = new ArrayList<>();
        boolean anyField = false;
        for (RawPart part : concat.parts()) {
            if (part.literal() != null) {
                pieces.add(part.literal());
                continue;
            }
            String value = join(strings(part.values(), column.output(), formatter, sheet, column, warnings), null);
            if (!value.isEmpty()) {
                anyField = true;
                pieces.add(part.label() == null || part.label().isEmpty() ? value : part.label() + concat.labelSeparator() + value);
            }
        }
        return anyField ? String.join(concat.separator(), pieces) : "";
    }

    private static List<String> strings(List<Object> values, OutputType output, ExportValueFormatter formatter,
                                        Sheet sheet, Column column, List<ExportWarning> warnings) {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            Optional<String> text = formatter.format(value, output);
            if (text.isEmpty()) {
                warnings.add(new ExportWarning(ExportWarning.Code.UNSUPPORTED_VALUE, sheet.name(), column.header(),
                        value.getClass().getSimpleName()));
            } else if (!text.get().isBlank()) {
                out.add(text.get());
            }
        }
        return out;
    }

    private static String join(List<String> values, @Nullable ListOptions list) {
        List<String> ordered = new ArrayList<>(values);
        if (list != null && list.sorted()) {
            ordered.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return String.join(list == null ? DEFAULT_LIST_SEPARATOR : list.separator(), ordered);
    }

    private static void sort(Sheet sheet, List<List<Object>> rows) {
        if (sheet.sortBy().isEmpty()) {
            return;
        }
        Comparator<List<Object>> comparator = null;
        for (String header : sheet.sortBy()) {
            int index = indexOf(sheet, header);
            Comparator<List<Object>> next = (a, b) -> compare(a.get(index), b.get(index));
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        rows.sort(comparator);
    }

    private static int indexOf(Sheet sheet, String header) {
        for (int i = 0; i < sheet.columns().size(); i++) {
            if (sheet.columns().get(i).header().equals(header)) return i;
        }
        throw new InvalidExportTemplateException("Unknown sort column: " + header);
    }

    private static int compare(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return Double.compare(x, y);
        if (a instanceof Double) return -1;
        if (b instanceof Double) return 1;
        return String.CASE_INSENSITIVE_ORDER.compare((String) a, (String) b);
    }

    private static List<ExportWarning> dedupe(List<ExportWarning> warnings) {
        return List.copyOf(new LinkedHashSet<>(warnings));
    }
}
