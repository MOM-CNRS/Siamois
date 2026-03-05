package fr.siamois.ui.bean;

import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
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



}
