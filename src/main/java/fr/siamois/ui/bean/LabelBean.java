package fr.siamois.ui.bean;

import fr.siamois.domain.events.ConceptChangeEvent;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.LabelType;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
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
    private final ConceptLabelRepository conceptLabelRepository;

    private final Map<String, Map<Concept, String>> prefLabelCache = new HashMap<>();
    private final Map<Long, ConceptLabel> idToLabelCache = new HashMap<>();

    @EventListener(ConceptChangeEvent.class)
    public void resetCache() {
        prefLabelCache.clear();
        idToLabelCache.clear();
    }

    private Optional<String> searchMatchingLangAndPrefLabel(String lang, Concept concept, List<ConceptPrefLabel> existingLabels) {
        if (prefLabelCache.containsKey(lang) && prefLabelCache.get(lang).containsKey(concept)) {
            return Optional.of(prefLabelCache.get(lang).get(concept));
        }

        if (existingLabels.isEmpty())
            existingLabels.addAll(conceptLabelRepository.findAllPrefLabelsByConcept(concept));

        return existingLabels.stream()
                .filter(data -> data.getLangCode().equalsIgnoreCase(lang))
                .findFirst()
                .map(ConceptPrefLabel::getLabel);
    }

    private void addToCache(String lang, Concept concept, String label) {
        prefLabelCache.computeIfAbsent(lang, k -> new HashMap<>()).put(concept, label);
    }

    public String findPrefLabelOf(ConceptAltLabel altLabel) {
        return findLabelOf(altLabel.getConcept());
    }
    /**
     * Find the best matching pref label for the given concept based on the user's preferred language
     * @param concept the concept to find the label for
     * @return the best matching label, or the concept's external ID if no label is found
     */
    public String findLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        List<ConceptPrefLabel> labels = new ArrayList<>();

        Optional<String> preferedLang = searchMatchingLangAndPrefLabel(userInfo.getLang(), concept, labels);
        if (preferedLang.isPresent()) {
            addToCache(userInfo.getLang(), concept, preferedLang.get());
            return preferedLang.get();
        }

        return concept.getExternalId();

    }

    public String findLabelFrom(ConceptLabel label) {
        if (label == null) return null;
        return label.getLabel();
    }


    public String findVocabularyLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo info = sessionSettingsBean.getUserInfo();
        return labelService.findLabelOf(concept.getVocabulary(), info.getLang()).getValue();
    }

    public ConceptLabel findPrefLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo info = sessionSettingsBean.getUserInfo();
        return labelService.findLabelOf(concept,  info.getLang());
    }

    public Optional<ConceptLabel> findById(Long id) {
        if (idToLabelCache.containsKey(id)) {
            return Optional.of(idToLabelCache.get(id));
        }
        Optional<ConceptLabel> label = conceptLabelRepository.findById(id);
        label.ifPresent(l -> idToLabelCache.put(id, l));
        return label;
    }

}
