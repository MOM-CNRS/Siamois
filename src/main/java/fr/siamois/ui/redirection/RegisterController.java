package fr.siamois.ui.redirection;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.ui.bean.RegisterBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@Slf4j
@Controller
@Scope(value = "session")
public class RegisterController {

    private final RegisterBean registerBean;
    private final PendingPersonService pendingPersonService;

    public RegisterController(RegisterBean registerBean, PendingPersonService pendingPersonService) {
        this.registerBean = registerBean;
        this.pendingPersonService = pendingPersonService;
    }

    @GetMapping("/register/{token}")
    public String goToRegister(@PathVariable String token) {
        Optional<PendingPerson> opt = pendingPersonService.findByToken(token);
        if (opt.isEmpty()) {
            log.error("No person found with token {}", token);
            return "redirect:/error/404";
        }

        PendingPerson pendingPerson = opt.get();
        // The pending person is kept so the member lists still display "invitation expired"
        // and a new invitation can be sent to the same email.
        if (pendingPersonService.invitationIsExpired(pendingPerson)) {
            log.error("Invitation expired for token {}", token);
            return "redirect:/error/404";
        }

        registerBean.init(pendingPerson);

        return "forward:/pages/login/register.xhtml";
    }

}
