package fr.siamois.ui.api.openapi.v1;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "Vérifie si le service est en ligne")
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
