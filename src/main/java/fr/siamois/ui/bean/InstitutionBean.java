package fr.siamois.ui.bean;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.util.ArrayList;
import java.util.List;

@Component
@SessionScoped
public class InstitutionBean {

    private final ConceptService conceptService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LabelService labelService;
    private final LangBean langBean;

    public InstitutionBean(ConceptService conceptService, SessionSettingsBean sessionSettingsBean, LabelService labelService, LangBean langBean) {
        this.conceptService = conceptService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.labelService = labelService;
        this.langBean = langBean;
    }


    public List<ConceptLabel> actionUnitTypeUsedInInstitution() {
        List<Concept> cList = conceptService.findAllByActionUnitOfInstitution(sessionSettingsBean.getSelectedInstitution());

        return new ArrayList<>(cList.stream()
                .map(concept -> labelService.findLabelOf(
                        concept, langBean.getLanguageCode()
                ))
                .toList());

    }

}
