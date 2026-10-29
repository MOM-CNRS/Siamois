package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.FullInfoDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads, when the application starts, the thesaurus concept of every system field, so the label and
 * definition a field shows are its concept's (see {@code FieldTextService}).
 * <p>
 * A thesaurus sync only loads the branches projects are configured with, and the concept of a plain
 * field (a date, a text, a link…) is on none of them. Concepts already loaded are skipped, so only the
 * first start (or a concept added to the thesaurus) costs remote calls. Like the default project
 * thesaurus, this one is not needed to run: an unreachable thesaurus is logged and the fields keep
 * their own texts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemFieldConceptInitializer implements DatabaseInitializer {

    private final VocabularyService vocabularyService;
    private final ConceptRepository conceptRepository;
    private final ConceptApi conceptApi;
    private final ConceptService conceptService;

    @Override
    public void initialize() {
        long begin = System.currentTimeMillis();
        int loaded = 0;
        int skipped = 0;
        try {
            for (Map.Entry<Vocabulary, Set<String>> entry : systemConceptIds().entrySet()) {
                Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(entry.getKey().getUri());
                for (String externalId : entry.getValue()) {
                    if (isLoaded(vocabulary, externalId)) {
                        skipped++;
                    } else if (load(vocabulary, externalId)) {
                        loaded++;
                    }
                }
            }
            log.info("System field concepts: {} loaded, {} already there, in {} ms", loaded, skipped,
                    System.currentTimeMillis() - begin);
        } catch (Exception e) {
            // Stops at the first failure: with the thesaurus unreachable, each remaining concept would wait for its own timeout.
            log.error("Could not load the system field concepts ({} loaded before): fields keep their own labels", loaded, e);
        }
    }

    private boolean isLoaded(Vocabulary vocabulary, String externalId) {
        return conceptRepository.findConceptByExternalIdIgnoreCase(vocabulary.getExternalVocabularyId(), externalId)
                .map(Concept::isLoaded)
                .orElse(false);
    }

    /** @return true when the concept was fetched and saved, false when the thesaurus does not know it */
    private boolean load(Vocabulary vocabulary, String externalId) throws RestClientException {
        FullInfoDTO info = conceptApi.fetchConceptInfo(vocabulary, externalId);
        if (info == null || info.getIdentifier() == null || info.getIdentifier().length == 0
                || info.getPrefLabel() == null || info.getPrefLabel().length == 0) {
            log.warn("The thesaurus returned no usable info for system field concept {}", externalId);
            return false;
        }
        conceptService.saveOrGetConceptFromFullDTO(vocabulary, info, null);
        return true;
    }

    /** The numeric concept ids of the system fields, by the vocabulary the code declares them in. */
    private Map<Vocabulary, Set<String>> systemConceptIds() {
        List<CustomField> fields = new ArrayList<>();
        for (ConfigurableTable table : ConfigurableTable.projectScopedTables()) {
            fields.addAll(SystemFieldCatalog.sharedFieldsOf(table));
        }
        Stream.of(ActionUnit.DETAILS_FORM, SpatialUnit.DETAILS_FORM)
                .forEach(form -> fields.addAll(new PanelFieldSource(form).getAllFields()));

        Map<Vocabulary, Set<String>> byVocabulary = new LinkedHashMap<>();
        for (CustomField field : fields) {
            Concept concept = field == null ? null : field.getConcept();
            if (concept == null || concept.getVocabulary() == null || concept.getExternalId() == null
                    || !concept.getExternalId().matches("\\d+")) {
                continue;
            }
            byVocabulary.computeIfAbsent(concept.getVocabulary(), v -> new LinkedHashSet<>()).add(concept.getExternalId());
        }
        return byVocabulary;
    }
}
