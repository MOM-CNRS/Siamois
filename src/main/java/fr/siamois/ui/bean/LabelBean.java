package fr.siamois.ui.bean;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.Optional;

@Component
@SessionScoped
@RequiredArgsConstructor
public class LabelBean implements Serializable {

    private final transient LabelService labelService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;

    public String findLabelOf(Concept concept) {
        if (concept == null) return null;
        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        Optional<LocalizedConceptData> optData = localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), userInfo.getLang());
        if (optData.isPresent()) {
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
