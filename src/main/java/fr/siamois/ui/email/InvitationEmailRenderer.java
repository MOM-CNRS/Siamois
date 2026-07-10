package fr.siamois.ui.email;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders the HTML invitation e-mail body from the {@code email-templates/invitation.html} template.
 * <p>
 * The template is read once from the classpath at startup; its {@code %s} placeholders are filled, in
 * order, with the scope name, the invitation link (used both as the {@code href} and as the visible
 * link text) and the availability period.
 */
@Component
public class InvitationEmailRenderer {

    static final String TEMPLATE_LOCATION = "email-templates/invitation.html";

    private final String template;

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
     * @param scopeName      the organisation / project name the person is invited to
     * @param invitationLink the registration link the invitee must follow to activate their account
     * @param availability   the period during which the link stays valid
     * @return the ready-to-send HTML body
     */
    public String render(String scopeName, String invitationLink, String availability) {
        return String.format(template, scopeName, invitationLink, invitationLink, availability);
    }
}
