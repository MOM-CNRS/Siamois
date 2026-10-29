package fr.siamois.domain.services.dataimport;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

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

    private final ConceptService conceptService;

    /**
     * @param configUrl    the project's table configuration, on the row's type when it is known and otherwise on
     *                     the table; {@code null} when the field belongs to no configurable table
     * @param thesaurusUrl the thesaurus branch of the field; {@code null} when no thesaurus is configured for it
     */
    public record VocabularyLinks(String configUrl, String thesaurusUrl) {

        // Expression Language 5 (Tomcat 10) reads bean getters, not record accessors
        public String getConfigUrl() {
            return configUrl;
        }

        public String getThesaurusUrl() {
            return thesaurusUrl;
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
            return new VocabularyLinks(null, null);
        }
        ImportError.VocabularyIssue issue = error.vocabulary();
        Long institutionId = project.getCreatedByInstitution() == null ? null : project.getCreatedByInstitution().getId();
        ConfigurableTable table = issue.fieldCode() != null
                ? ConfigurableTable.ofFieldCode(issue.fieldCode())
                : TABLE_OF_SHEET.get(error.sheet());
        // a label looked up for a field: the field's branch ; a concept that is not loaded: that concept
        String thesaurusUrl = issue.fieldCode() != null ? thesaurusUrl(project, institutionId, issue.fieldCode()) : issue.conceptUrl();
        return new VocabularyLinks(configUrl(project, institutionId, table, issue, contextPath == null ? "" : contextPath), thesaurusUrl);
    }

    private String configUrl(ActionUnitDTO project, Long institutionId, ConfigurableTable table, ImportError.VocabularyIssue issue,
                             String contextPath) {
        if (table == null) {
            return null;
        }
        StringBuilder url = new StringBuilder(contextPath)
                .append("/settings/project/").append(project.getId()).append("/tables?table=").append(table.name());
        typeIdOf(institutionId, table, issue).ifPresent(typeId -> url.append("&typeId=").append(typeId));
        return url.toString();
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
