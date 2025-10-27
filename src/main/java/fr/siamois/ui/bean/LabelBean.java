package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.ConceptChangeEvent;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@SessionScoped
@RequiredArgsConstructor
public class LabelBean implements Serializable {

    private final transient LabelService labelService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;

    private final Map<String, Map<Concept, String>> labelCache = new HashMap<>();

    @EventListener(ConceptChangeEvent.class)
    public void resetCache() {
        labelCache.clear();
    }

    public String findLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo userInfo = sessionSettingsBean.getUserInfo();

        if (labelCache.containsKey(userInfo.getLang()) && labelCache.get(userInfo.getLang()).containsKey(concept)) {
            return labelCache.get(userInfo.getLang()).get(concept);
        }

        Optional<LocalizedConceptData> optData = localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), userInfo.getLang());
        if (optData.isPresent()) {
            labelCache.computeIfAbsent(userInfo.getLang(), k -> new HashMap<>()).put(concept, optData.get().getLabel());
            return optData.get().getLabel();
        }
        return concept.getExternalId();
    }

    public String findVocabularyLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo info = sessionSettingsBean.getUserInfo();
        return labelService.findLabelOf(concept.getVocabulary(), info.getLang()).getValue();
    }

}
