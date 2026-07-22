package fr.siamois.ui.email;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders the HTML invitation e-mail body from the {@code email-templates/invitation.html} template.
 * <p>
 * The template is read once from the classpath at startup; its indexed {@code %n$s} placeholders are
 * filled with the inviter's name, the scope name (application / institution / project), the profiles
 * granted to the invitee, the invitation link (used both as the {@code href} and as the visible link
 * text) and the availability period.
 */
@Component
public class InvitationEmailRenderer {

    static final String TEMPLATE_LOCATION = "email-templates/invitation.html";

    private final String template;

    /**
     * Loads the invitation e-mail template from the classpath once, at bean construction time, so that
     * subsequent {@link #render} calls only have to fill in its placeholders.
     */
    public InvitationEmailRenderer() {
        this.template = loadTemplate();
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_LOCATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load invitation e-mail template " + TEMPLATE_LOCATION, e);
        }
    }

    /**
     * @param inviterName    the name of the user who sent the invitation
     * @param scopeName      the application / institution / project name the person is invited to
     * @param profiles       the profiles granted to the invitee within that scope
     * @param invitationLink the registration link the invitee must follow to activate their account
     * @param availability   the period during which the link stays valid
     * @return the ready-to-send HTML body
     */
    public String render(String inviterName, String scopeName, String profiles, String invitationLink, String availability) {
        return String.format(template, inviterName, scopeName, profiles, invitationLink, availability);
    }
}
