package fr.siamois.ui.api.openapi.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Ping", description = "Endpoints de test")
public class PingController {

    @Operation(summary = "Vérifie si le service est en ligne")
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
