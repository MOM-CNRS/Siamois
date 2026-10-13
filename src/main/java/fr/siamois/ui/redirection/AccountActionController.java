package fr.siamois.ui.redirection;

import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.ui.bean.ConfirmEmailBean;
import fr.siamois.ui.bean.ForgotPasswordBean;
import fr.siamois.ui.bean.ResetPasswordBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * Landing points of the links e-mailed for a password reset and an e-mail change confirmation.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountActionController {

    private static final String NOT_FOUND = "redirect:/error/404";

    private final PersonActionTokenService tokenService;
    private final ResetPasswordBean resetPasswordBean;
    private final ConfirmEmailBean confirmEmailBean;
    private final ForgotPasswordBean forgotPasswordBean;

    @GetMapping("/forgot-password")
    public String goToForgotPassword() {
        forgotPasswordBean.init();
        return "forward:/pages/login/forgotPassword.xhtml";
    }

    @GetMapping("/reset-password/{token}")
    public String goToResetPassword(@PathVariable String token) {
        Optional<PersonActionToken> opt = tokenService.findValid(token, PersonActionType.PASSWORD_RESET);
        if (opt.isEmpty()) {
            log.warn("Unknown or expired password reset token");
            return NOT_FOUND;
        }
        resetPasswordBean.init(token);
        return "forward:/pages/login/resetPassword.xhtml";
    }

    @GetMapping("/confirm-email/{token}")
    public String confirmEmail(@PathVariable String token) {
        Optional<PersonActionToken> opt = tokenService.findValid(token, PersonActionType.EMAIL_CHANGE);
        if (opt.isEmpty()) {
            log.warn("Unknown or expired e-mail change token");
            return NOT_FOUND;
        }
        confirmEmailBean.init(token, opt.get().getNewEmail());
        return "forward:/pages/login/confirmEmail.xhtml";
    }
}
