package fr.siamois.ui.redirection;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.ui.bean.RegisterBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.OffsetDateTime;
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
        if (invitationIsExpired(pendingPerson)) {
            log.error("Invitation expired for token {}", token);
            pendingPersonService.delete(pendingPerson);
            return "redirect:/error/404";
        }

        registerBean.init(opt.get());

        return "forward:/pages/login/register.xhtml";
    }

    private static boolean invitationIsExpired(PendingPerson pendingPerson) {
        return OffsetDateTime.now().isAfter(pendingPerson.getPendingInvitationExpirationDate());
    }

}
