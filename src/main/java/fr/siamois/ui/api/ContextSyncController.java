package fr.siamois.ui.api;

import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.FlowBean;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextSyncController {

    private final SessionSettingsBean sessionSettingsBean;
    private final FlowBean flowBean;

    @GetMapping("/check")
    public Map<String, Object> checkContext(
            @RequestParam Long institutionId,
            @RequestParam String panelIds,
            HttpSession session) {

        boolean needsReload =
                         !sessionSettingsBean.getSelectedInstitution().getId().equals(institutionId)
                        || !flowBean.getCurrentPanelIdsAsString().equals(panelIds);

        return Map.of("reload", needsReload);
    }
}