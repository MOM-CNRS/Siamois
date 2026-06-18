package fr.siamois.ui.api;

import fr.siamois.domain.services.ark.ArkRedirectionService;
import fr.siamois.ui.bean.RedirectBean;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@RestController
@RequestMapping("/api/ark:/{naan}/{qualifier}")
@Hidden
public class ArkController {

    private final ArkRedirectionService arkRedirectionService;
    private final RedirectBean redirectBean;

    public ArkController(ArkRedirectionService arkRedirectionService, RedirectBean redirectBean) {
        this.arkRedirectionService = arkRedirectionService;
        this.redirectBean = redirectBean;
    }

    @GetMapping
    public ResponseEntity<Void> redirectToRessource(@PathVariable String naan, @PathVariable String qualifier) throws URISyntaxException {
        Optional<URI> uri = arkRedirectionService.getResourceUriFromArk(naan, qualifier);

        URI notfoundError = new URI(redirectBean.redirectUrl("/error/404"));

        return uri.<ResponseEntity<Void>>map(
                        value -> ResponseEntity
                                .status(HttpStatus.FOUND)
                                .location(value)
                                .build()
                )
                .orElseGet(
                        () -> ResponseEntity
                                .status(HttpStatus.MOVED_PERMANENTLY)
                                .location(notfoundError)
                                .build()
                );
    }

}
