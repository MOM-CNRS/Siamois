package fr.siamois.ui.redirection;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.ui.bean.RegisterBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@Slf4j
@Controller
@Scope(value = "session")
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterBean registerBean;
    private final PendingPersonService pendingPersonService;

    @GetMapping("/register/{token}")
    public String goToRegister(@PathVariable String token) {
        Optional<PendingPerson> opt = pendingPersonService.findByToken(token);
        if (opt.isEmpty()) {
            log.error("No person found with token {}", token);
            return "redirect:/error/404";
        }

        PendingPerson pendingPerson = opt.get();
        if (pendingPersonService.isExpired(pendingPerson)) {
            // The invitation is kept in database (never auto-deleted): a manager can renew it from the
            // members page, which replaces this expired link with a fresh one.
            log.warn("Invitation expired for token {}", token);
            return "redirect:/error/404";
        }

        registerBean.init(opt.get());

        return "forward:/pages/login/register.xhtml";
    }

}
