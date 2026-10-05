package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nom du fichier d'un export : le patron du modèle (ou {@code {template}_{identifier}_{date}}) dont les
 * champs {@code {date}}, {@code {dateCompact}}, {@code {identifier}}, {@code {name}}, {@code {oaCode}} et
 * {@code {template}} sont remplacés, puis rendu sûr (ASCII, sans séparateurs de chemin).
 */
final class ExportFileName {

    private static final String DEFAULT_PATTERN = "{template}_{identifier}_{date}";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private ExportFileName() {
        throw new UnsupportedOperationException();
    }

    static String of(ExportTemplateDefinition definition, ActionUnit project, List<ExportWarning> warnings) {
        String pattern = definition.fileNamePattern() == null || definition.fileNamePattern().isBlank()
                ? DEFAULT_PATTERN : definition.fileNamePattern();
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = switch (matcher.group(1)) {
                case "date" -> LocalDate.now().toString();
                case "dateCompact" -> LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                case "identifier" -> project.getFullIdentifier();
                case "name" -> project.getName();
                case "oaCode" -> project.getOaCode();
                case "template" -> definition.name();
                default -> {
                    warnings.add(new ExportWarning(ExportWarning.Code.UNKNOWN_PLACEHOLDER, null, null, matcher.group(1)));
                    yield "";
                }
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        String ascii = Normalizer.normalize(out.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String safe = ascii.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[_.]+|_+$", "");
        return (safe.isEmpty() ? "export" : safe) + ".xlsx";
    }
}
