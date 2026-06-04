package fr.siamois.ui.bean.panel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.SearchResultDTO;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.bean.FocusViewBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
@Setter
public class SearchBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient SearchRepository searchRepository;
    private final FlowBean flowBean;

    @Nullable
    private SearchResultDTO selected;

    private UserInfo userInfo;

    @PostConstruct
    public void init() {
        userInfo = sessionSettingsBean.getUserInfo();
    }

    public List<SearchResultDTO> completeText(String input) {
        return searchRepository.findResultsFor(input, userInfo.getInstitution(), userInfo.getUser());
    }

    public void onResultSelect(AjaxBehaviorEvent event) {
        if (selected == null) return;
        FacesContext ctx = FacesContext.getCurrentInstance();
        FocusViewBean focusViewBean = ctx.getApplication()
                .evaluateExpressionGet(ctx, "#{focusViewBean}", FocusViewBean.class);
        if (focusViewBean == null || focusViewBean.getMainPanel() == null) return;

        var panel = focusViewBean.getMainPanel();
        if (selected.getRecordingUnitId() != null) {
            flowBean.addRecordingUnitToOverview(selected.getRecordingUnitId(), panel, null);
        } else if (selected.getSpatialUnitId() != null) {
            flowBean.addSpatialUnitToOverview(selected.getSpatialUnitId(), panel, null);
        } else if (selected.getActionUnitId() != null) {
            flowBean.addActionUnitToOverview(selected.getActionUnitId(), panel, null);
        } else if (selected.getSpecimenId() != null) {
            flowBean.addSpecimenToOverview(selected.getSpecimenId(), panel, null);
        }
    }

}
