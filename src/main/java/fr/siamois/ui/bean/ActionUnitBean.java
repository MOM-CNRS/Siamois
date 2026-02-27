package fr.siamois.ui.bean;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;


@Component
@SessionScope
public class ActionUnitBean {

    private final ActionUnitService service;
    private final SessionSettingsBean sessionSettingsBean;
    private final FlowBean flowBean;

    public ActionUnitBean(ActionUnitService service, SessionSettingsBean sessionSettingsBean, FlowBean flowBean) {
        this.service = service;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
    }


}
