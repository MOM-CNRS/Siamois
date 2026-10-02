package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ColumnField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConcatPart;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConcatRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConstantRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.DirectRule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.EntitySource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.FieldRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ProjectSource;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Rule;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Sheet;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.Source;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.TechnicalSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vérifie ce que la grammaire JSON ne peut pas voir : que les clés de sources techniques, les chemins
 * et les colonnes nommées d'un modèle existent dans les registres du code ({@link ExportTechnicalSource},
 * {@link ExportNavigations}). Chaque problème est un message lisible, préfixé de son emplacement.
 * Ne dépend d'aucune donnée : un modèle valide ici reste valable pour tous les projets.
 */
public final class ExportTemplateChecker {

    private ExportTemplateChecker() {
        throw new UnsupportedOperationException();
    }

    /** @return les problèmes trouvés ; liste vide si le modèle est cohérent avec les registres */
    public static List<String> check(ExportTemplateDefinition definition) {
        List<String> problems = new ArrayList<>();
        for (Sheet sheet : definition.sheets()) {
            checkSheet(sheet, problems);
        }
        return problems;
    }

    private static void checkSheet(Sheet sheet, List<String> problems) {
        for (int i = 0; i < sheet.sources().size(); i++) {
            Source source = sheet.sources().get(i);
            if (source instanceof TechnicalSource t && ExportTechnicalSource.ofKey(t.key()).isEmpty()) {
                problems.add(sheet.name() + " / source " + (i + 1) + " : unknown technical source '" + t.key() + "'");
            }
        }
        for (ExportTemplateDefinition.Column column : sheet.columns()) {
            for (Rule rule : column.rules()) {
                String where = sheet.name() + " / " + column.header();
                for (int index : targets(rule, sheet.sources().size())) {
                    checkRule(rule, sheet.sources().get(index), where, problems);
                }
            }
        }
    }

    private static List<Integer> targets(Rule rule, int sourceCount) {
        if (!rule.sources().isEmpty()) {
            return rule.sources();
        }
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < sourceCount; i++) all.add(i);
        return all;
    }

    private static void checkRule(Rule rule, Source source, String where, List<String> problems) {
        if (rule instanceof DirectRule r) {
            checkField(r.field(), r.path(), source, where, problems);
        } else if (rule instanceof ConcatRule r) {
            for (ConcatPart part : r.parts()) {
                if (part.field() != null) {
                    checkField(part.field(), part.path(), source, where, problems);
                }
            }
        } else if (!(rule instanceof ConstantRule)) {
            problems.add(where + " : unsupported rule");
        }
    }

    private static void checkField(FieldRef field, List<String> path, Source source, String where, List<String> problems) {
        Optional<ExportTechnicalSource> technical = source instanceof TechnicalSource t
                ? ExportTechnicalSource.ofKey(t.key()) : Optional.empty();
        if (source instanceof TechnicalSource && technical.isEmpty()) {
            return; // déjà signalé au niveau de la source
        }
        if (field instanceof ColumnField column) {
            checkColumnField(column, path, technical, where, problems);
            return;
        }
        try {
            if (technical.isPresent()) {
                ExportNavigations.targetOf(technical.get(), path);
            } else {
                ExportNavigations.targetOf(subjectOf(source), path);
            }
        } catch (IllegalArgumentException e) {
            problems.add(where + " : " + e.getMessage());
        }
    }

    private static void checkColumnField(ColumnField column, List<String> path, Optional<ExportTechnicalSource> technical,
                                         String where, List<String> problems) {
        if (technical.isEmpty()) {
            problems.add(where + " : the column '" + column.name() + "' only exists on a technical source");
        } else if (!path.isEmpty()) {
            problems.add(where + " : a column of a technical source cannot have a path");
        } else if (!technical.get().valueNames().contains(column.name())) {
            problems.add(where + " : unknown column '" + column.name() + "' of " + technical.get());
        }
    }

    /** Entité que lit une source (hors table technique). */
    static ExportSubject subjectOf(Source source) {
        if (source instanceof EntitySource e) {
            return ExportSubject.of(e.entity());
        }
        if (source instanceof ProjectSource) {
            return ExportSubject.PROJECT;
        }
        throw new IllegalArgumentException("A technical source has no single entity");
    }
}
