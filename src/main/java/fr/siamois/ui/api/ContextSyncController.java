package fr.siamois.ui.api;

import fr.siamois.ui.bean.SessionSettingsBean;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.Map;

@ApiIgnore
@Hidden
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextSyncController {

    private final SessionSettingsBean sessionSettingsBean;

    /**
     * Tells the client whether its organisation still matches the one selected in the JSF session.
     *
     * @param institutionId the organisation the client is currently displaying
     * @return a map whose {@code reload} entry is {@code true} when the client must reload its context
     */
    @GetMapping("/check")
    public Map<String, Object> checkContext(
            @RequestParam Long institutionId) {

        boolean needsReload = !institutionId.equals(sessionSettingsBean.getSelectedInstitutionId());

        return Map.of("reload", needsReload);
    }
}