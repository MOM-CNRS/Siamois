package fr.siamois.ui.bean;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.Objects;

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

    public boolean showCreateRecordingUnitButton(TraceableEntity context) {
        if(context.getClass() == ActionUnit.class) {
            return Objects.equals(flowBean.getIsWriteMode(), true) &&
                    service.canCreateRecordingUnit(sessionSettingsBean.getUserInfo(), (ActionUnit) context) ;
        }
        else {
            return false;
        }

    }

}
