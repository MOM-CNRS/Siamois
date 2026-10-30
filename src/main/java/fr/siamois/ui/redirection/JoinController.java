package fr.siamois.ui.redirection;

import fr.siamois.domain.services.auth.InvitationLinkService;
import fr.siamois.ui.bean.JoinBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Landing point of an invitation link: the sign-up page, or a 404 when the link cannot be used any more
 * (unknown, revoked, expired or full), without telling which.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class JoinController {

    private final InvitationLinkService invitationLinkService;
    private final JoinBean joinBean;

    @GetMapping("/join/{token}")
    public String goToJoin(@PathVariable String token) {
        if (!invitationLinkService.isUsable(token)) {
            log.warn("Unusable invitation link opened");
            return "redirect:/error/404";
        }
        joinBean.init(token);
        return "forward:/pages/login/join.xhtml";
    }
}
