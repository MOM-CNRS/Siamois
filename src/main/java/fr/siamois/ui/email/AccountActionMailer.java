package fr.siamois.ui.email;

import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders and sends the e-mails carrying a {@link PersonActionToken} link (password reset, e-mail
 * change confirmation) from the {@code email-templates/account-action.html} template.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountActionMailer {

    static final String TEMPLATE_LOCATION = "email-templates/account-action.html";

    private final EmailManager emailManager;
    private final PersonActionTokenService tokenService;
    private final LangBean langBean;
    private final String template = loadTemplate();

    private static String loadTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_LOCATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load e-mail template " + TEMPLATE_LOCATION, e);
        }
    }

    /**
     * Sends the e-mail of the given token to {@code recipient}.
     *
     * @return {@code true} when the e-mail was handed to the transport, {@code false} when delivery failed
     */
    public boolean send(PersonActionToken token, String recipient) {
        String prefix = token.getType() == PersonActionType.PASSWORD_RESET ? "mail.passwordReset" : "mail.emailChange";
        String body = String.format(template,
                langBean.msg(prefix + ".greeting"),
                langBean.msg(prefix + ".intro"),
                tokenService.link(token),
                langBean.msg("mail.accountAction.validity", DateUtils.formatOffsetDateTime(token.getExpirationDate())),
                langBean.msg("mail.accountAction.ignore"));
        try {
            emailManager.sendEmail(recipient, langBean.msg(prefix + ".subject"), body, EmailManager.TEXT_HTML);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to send account e-mail to {}", recipient, e);
            return false;
        }
    }
}
