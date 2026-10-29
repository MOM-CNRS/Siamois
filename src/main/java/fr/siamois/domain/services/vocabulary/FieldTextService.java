package fr.siamois.domain.services.vocabulary;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The label and definition a field shows: the ones of its concept in the thesaurus, and only when the
 * field has no concept — or its concept is not in the database, or has no label / definition in the
 * requested language — the field's own (its message key, its hint).
 * <p>
 * A system field's concept is built in code and never persisted with its row (see
 * {@code SystemFieldInitializer}), so it is looked up by its vocabulary and external id, the same way
 * for system and additional fields. The lookups are cached for a few minutes: the same few dozen
 * concepts serve every request, and a thesaurus sync is the only thing that changes them.
 * <p>
 * The concepts of the system fields are loaded at startup ({@code SystemFieldConceptInitializer}); a
 * field whose concept could not be loaded keeps its own texts.
 */
@Service
@RequiredArgsConstructor
public class FieldTextService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int CACHE_SIZE = 4000;
    private static final String DEFAULT_LANG = "fr";

    /** What the field displays: the label, and the definition shown as its tooltip. */
    public record FieldTexts(String label, @Nullable String definition) {
    }

    private record ConceptKey(String baseUri, String vocabularyId, String externalId, String lang) {
    }

    private record ConceptTexts(@Nullable String label, @Nullable String definition) {
        static final ConceptTexts NONE = new ConceptTexts(null, null);
    }

    private final ConceptRepository conceptRepository;
    private final ConceptLabelRepository conceptLabelRepository;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;

    private final Cache<ConceptKey, ConceptTexts> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(CACHE_SIZE)
            .build();

    /**
     * @param field         the field to describe
     * @param lang          ISO language code of the reader
     * @param fallbackLabel the field's own label, already translated: used when its concept has none
     * @param fallbackHint  the field's own hint, already translated: used when its concept has no definition
     * @return the label and definition to display
     */
    public FieldTexts resolve(CustomField field, @Nullable String lang, @Nullable String fallbackLabel,
                              @Nullable String fallbackHint) {
        ConceptTexts texts = textsOf(field == null ? null : field.getConcept(), lang);
        return new FieldTexts(
                texts.label() != null ? texts.label() : fallbackLabel,
                texts.definition() != null ? texts.definition() : blankToNull(fallbackHint));
    }

    /**
     * Same as {@link #resolve} for a system field known only by its id (the field configuration
     * screens carry ids, not definitions). An id that is not a system field gets the fallbacks.
     */
    public FieldTexts resolveSystemField(@Nullable Long fieldId, @Nullable String lang, @Nullable String fallbackLabel,
                                         @Nullable String fallbackHint) {
        CustomField field = fieldId == null ? null : SystemFields.BY_ID.get(fieldId);
        return resolve(field, lang, fallbackLabel, fallbackHint);
    }

    /** The system fields of every table by id, built once on first use (class initialization is thread-safe). */
    private static final class SystemFields {
        static final Map<Long, CustomField> BY_ID = byId();

        private static Map<Long, CustomField> byId() {
            Map<Long, CustomField> map = new HashMap<>();
            for (ConfigurableTable table : ConfigurableTable.values()) {
                for (CustomField field : SystemFieldCatalog.sharedFieldsOf(table)) {
                    if (field.getId() != null) {
                        map.putIfAbsent(field.getId(), field);
                    }
                }
            }
            return Map.copyOf(map);
        }
    }

    private ConceptTexts textsOf(@Nullable Concept concept, @Nullable String lang) {
        if (concept == null || concept.getExternalId() == null || concept.getVocabulary() == null) {
            return ConceptTexts.NONE;
        }
        Vocabulary vocabulary = concept.getVocabulary();
        String language = lang == null || lang.isBlank() ? DEFAULT_LANG : lang.trim().toLowerCase(Locale.ROOT);
        ConceptKey key = new ConceptKey(vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId(),
                concept.getExternalId(), language);
        return cache.get(key, k -> load(concept, k));
    }

    private ConceptTexts load(Concept declared, ConceptKey key) {
        Optional<Concept> stored = declared.getId() != null
                ? Optional.of(declared)
                : conceptRepository.findConceptByExternalIdIgnoreCase(key.vocabularyId(), key.externalId());
        if (stored.isEmpty() || stored.get().getId() == null) {
            return ConceptTexts.NONE;
        }
        Concept concept = stored.get();
        String label = conceptLabelRepository.findPrefLabelByLangCodeAndConcept(key.lang(), concept)
                .map(ConceptPrefLabel::getLabel)
                .map(FieldTextService::blankToNull)
                .orElse(null);
        String definition = localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), key.lang())
                .map(LocalizedConceptData::getDefinition)
                .map(FieldTextService::plainText)
                .orElse(null);
        return new ConceptTexts(label, definition);
    }

    /**
     * A thesaurus definition as plain text: the national reference writes them with HTML paragraphs
     * and some are escaped twice ({@code &amp;amp;}), which a tooltip would show as is.
     */
    static String plainText(@Nullable String definition) {
        if (definition == null) {
            return null;
        }
        String text = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(definition));
        text = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
