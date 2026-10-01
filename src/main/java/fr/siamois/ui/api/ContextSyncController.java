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

    @GetMapping("/check")
    public Map<String, Object> checkContext(
            @RequestParam Long institutionId) {

        boolean needsReload = !sessionSettingsBean.getSelectedInstitution().getId().equals(institutionId);

        return Map.of("reload", needsReload);
    }
}