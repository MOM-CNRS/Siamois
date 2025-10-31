package fr.siamois.ui.bean;

import fr.siamois.domain.events.ConceptChangeEvent;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LabelType;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.*;

@Component
@SessionScoped
@RequiredArgsConstructor
public class LabelBean implements Serializable {

    private final transient LabelService labelService;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient LocalizedConceptDataRepository localizedConceptDataRepository;

    private static final List<String> FALLBACK_LANGS = List.of("en", "fr");

    private final Map<String, Map<Concept, String>> labelCache = new HashMap<>();

    @EventListener(ConceptChangeEvent.class)
    public void resetCache() {
        labelCache.clear();
    }

    private Optional<String> searchMatchingLang(String lang, Concept concept, List<LocalizedConceptData> existingLabels) {
        if (labelCache.containsKey(lang) && labelCache.get(lang).containsKey(concept)) {
            return Optional.of(labelCache.get(lang).get(concept));
        }

        if (existingLabels.isEmpty())
            existingLabels.addAll(localizedConceptDataRepository.findAllByConcept(concept));

        return existingLabels.stream()
                .filter(data -> data.getLangCode().equalsIgnoreCase(lang))
                .findFirst()
                .map(LocalizedConceptData::getLabel);
    }

    private String labelWithLangTag(String label, String lang) {
        return label + " (" + lang + ")";
    }

    private void addToCache(String lang, Concept concept, String label) {
        labelCache.computeIfAbsent(lang, k -> new HashMap<>()).put(concept, label);
    }

    /**
     * Find the pref label of the given concept label
     * @param conceptLabel the concept label
     * @return the pref label
     */
    public String findPrefLabelof(ConceptLabel conceptLabel) {
        if (conceptLabel.getLabelType() == LabelType.PREF_LABEL) {
            return conceptLabel.getLabel();
        } else {
            return findLabelOf(conceptLabel.getConcept());
        }
    }

    /**
     * Find the best matching pref label for the given concept based on the user's preferred language
     * @param concept the concept to find the label for
     * @return the best matching label, or the concept's external ID if no label is found
     */
    public String findLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        List<LocalizedConceptData> labels = new ArrayList<>();

        Optional<String> preferedLang = searchMatchingLang(userInfo.getLang(), concept, labels);
        if (preferedLang.isPresent()) {
            addToCache(userInfo.getLang(), concept, preferedLang.get());
            return preferedLang.get();
        }

        for (String lang : FALLBACK_LANGS) {
            Optional<String> optFallbackLang = searchMatchingLang(lang, concept, labels);
            if (optFallbackLang.isPresent()) {
                addToCache(lang, concept, optFallbackLang.get());
                return labelWithLangTag(optFallbackLang.get(), lang);
            }
        }

        if (!labels.isEmpty()) {
            addToCache(labels.get(0).getLangCode(), concept, labels.get(0).getLabel());
            return labelWithLangTag(labels.get(0).getLabel(), labels.get(0).getLangCode());
        }

        return concept.getExternalId();
    }

    public String findVocabularyLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo info = sessionSettingsBean.getUserInfo();
        return labelService.findLabelOf(concept.getVocabulary(), info.getLang()).getValue();
    }

}
