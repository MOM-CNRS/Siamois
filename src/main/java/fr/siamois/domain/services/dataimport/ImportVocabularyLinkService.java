package fr.siamois.domain.services.dataimport;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where to send the user when an import label does not resolve in the thesaurus: to the configuration of the
 * field in the project, and to the branch of the thesaurus the label is looked up in.
 */
@Service
@RequiredArgsConstructor
public class ImportVocabularyLinkService {

    /** The table a saved sheet belongs to, for the errors that name a table rather than a field. */
    private static final Map<String, ConfigurableTable> TABLE_OF_SHEET = Map.of(
            ImportSchema.RECORDING_UNIT, ConfigurableTable.UE,
            ImportSchema.SPECIMEN, ConfigurableTable.MOBILIER,
            ImportSchema.PHASE, ConfigurableTable.PHASE);

    private static final Pattern THESAURUS_IN_URL = Pattern.compile("[?&]idt=([^&#]+)");

    private final ConceptService conceptService;

    /** What the configuration link opens on, from the widest to the most precise. */
    public enum ConfigKind { TABLE, TYPE, FIELD }

    /**
     * @param configUrl     the project's table configuration, on the row's type and the field when they are known and
     *                      otherwise on the table; {@code null} when the field belongs to no configurable table
     * @param configKind    what {@code configUrl} opens on
     * @param configTarget  the name of the table or of the type the link is about (unused for a field)
     * @param thesaurusUrl  the thesaurus branch of the field, or the concept that is missing; {@code null} when none is known
     * @param thesaurusName the thesaurus {@code thesaurusUrl} points into; {@code null} when it is not known
     */
    public record VocabularyLinks(String configUrl, ConfigKind configKind, String configTarget, String thesaurusUrl,
                                  String thesaurusName) {

        public static final VocabularyLinks NONE = new VocabularyLinks(null, null, null, null, null);

        // Expression Language 5 (Tomcat 10) reads bean getters, not record accessors
        public String getConfigUrl() {
            return configUrl;
        }

        public String getConfigKind() {
            return configKind == null ? null : configKind.name();
        }

        public String getConfigTarget() {
            return configTarget;
        }

        public String getThesaurusUrl() {
            return thesaurusUrl;
        }

        public String getThesaurusName() {
            return thesaurusName;
        }

        public boolean hasAny() {
            return configUrl != null || thesaurusUrl != null;
        }
    }

    /**
     * @param contextPath the application's context path, prefixed to the configuration link
     */
    public VocabularyLinks linksFor(ActionUnitDTO project, ImportError error, String contextPath) {
        if (project == null || project.getId() == null || error == null || !error.isVocabularyError()) {
            return VocabularyLinks.NONE;
        }
        ImportError.VocabularyIssue issue = error.vocabulary();
        Long institutionId = project.getCreatedByInstitution() == null ? null : project.getCreatedByInstitution().getId();
        ConfigurableTable table = issue.fieldCode() != null
                ? ConfigurableTable.ofFieldCode(issue.fieldCode())
                : TABLE_OF_SHEET.get(error.sheet());

        VocabularyLinks config = configLink(project, institutionId, table, issue, contextPath == null ? "" : contextPath);

        // a label looked up for a field: the field's branch ; a concept that is not loaded: that concept
        String thesaurusUrl;
        String thesaurusName;
        if (issue.fieldCode() != null) {
            thesaurusUrl = thesaurusUrl(project, institutionId, issue.fieldCode());
            thesaurusName = thesaurusName(project, institutionId, issue.fieldCode());
        } else {
            thesaurusUrl = issue.conceptUrl();
            thesaurusName = nameInUrl(thesaurusUrl);
        }
        return new VocabularyLinks(config.configUrl(), config.configKind(), config.configTarget(), thesaurusUrl, thesaurusName);
    }

    /** The configuration half of the links: the table, or the row's type and the field when the type resolves. */
    private VocabularyLinks configLink(ActionUnitDTO project, Long institutionId, ConfigurableTable table,
                                       ImportError.VocabularyIssue issue, String contextPath) {
        if (table == null) {
            return VocabularyLinks.NONE;
        }
        StringBuilder url = new StringBuilder(contextPath)
                .append("/settings/project/").append(project.getId()).append("/tables?table=").append(table.name());
        Optional<Long> typeId = typeIdOf(institutionId, table, issue);
        if (typeId.isEmpty()) {
            return new VocabularyLinks(url.toString(), ConfigKind.TABLE, table.getLabel(), null, null);
        }
        url.append("&typeId=").append(typeId.get());
        boolean aField = issue.fieldCode() != null;
        if (aField) {
            url.append("&fieldCode=").append(URLEncoder.encode(issue.fieldCode(), StandardCharsets.UTF_8));
        }
        return new VocabularyLinks(url.toString(), aField ? ConfigKind.FIELD : ConfigKind.TYPE, issue.typeLabel(), null, null);
    }

    /** The {@code idt} of an OpenTheso link, which is the external id of its thesaurus. */
    private static String nameInUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = THESAURUS_IN_URL.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The row's type, when it has one that resolves (it does not when the failing label is the type itself). */
    private Optional<Long> typeIdOf(Long institutionId, ConfigurableTable table, ImportError.VocabularyIssue issue) {
        if (institutionId == null || issue.typeLabel() == null || table.getFieldCode().equals(issue.fieldCode())) {
            return Optional.empty();
        }
        try {
            return Optional.of(conceptService.resolveConceptByLabel(institutionId, table.getFieldCode(), issue.typeLabel()).getId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String thesaurusName(ActionUnitDTO project, Long institutionId, String fieldCode) {
        if (institutionId == null) {
            return null;
        }
        try {
            return conceptService.findFieldVocabularyId(institutionId, project.getId(), fieldCode).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String thesaurusUrl(ActionUnitDTO project, Long institutionId, String fieldCode) {
        if (institutionId == null) {
            return null;
        }
        try {
            return conceptService.findFieldBranchUri(institutionId, project.getId(), fieldCode).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
