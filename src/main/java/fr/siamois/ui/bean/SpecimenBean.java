package fr.siamois.ui.bean;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;


@Component
@SessionScope
public class SpecimenBean {

    private final RecordingUnitService service;
    private final SessionSettingsBean sessionSettingsBean;
    private final FlowBean flowBean;

    public SpecimenBean(RecordingUnitService service, SessionSettingsBean sessionSettingsBean, FlowBean flowBean) {
        this.service = service;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
    }


    public boolean showCreateSpecimenButton(TraceableEntity context) {
        if(context.getClass() == RecordingUnit.class) {
            return flowBean.getIsWriteMode() &&
                    service.canCreateSpecimen(sessionSettingsBean.getUserInfo(), (RecordingUnit) context) ;
        }
        else {
            return false;
        }

    }

}
