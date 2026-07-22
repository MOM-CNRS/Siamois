package fr.siamois.ui.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvitationEmailRendererTest {

    private final InvitationEmailRenderer renderer = new InvitationEmailRenderer();

    @Test
    void render_fillsTemplatePlaceholders() {
        String body = renderer.render("Jane Doe", "Ma Structure", "Coordinateur, Lecteur",
                "https://siamois.eu/register/token123", "3 jours");

        assertTrue(body.contains("Jane Doe"), "inviter name should be injected");
        assertTrue(body.contains("Ma Structure"), "scope name should be injected");
        assertTrue(body.contains("Coordinateur, Lecteur"), "profiles should be injected");
        assertTrue(body.contains("href=\"https://siamois.eu/register/token123\""), "link should be the href");
        assertTrue(body.contains(">https://siamois.eu/register/token123</a>"), "link should be the visible text");
        assertTrue(body.contains("3 jours"), "availability should be injected");
    }

    @Test
    void render_isHtmlWithNoRemainingPlaceholder() {
        String body = renderer.render("Jane Doe", "Org", "Lecteur", "https://siamois.eu/register/abc", "3 jours");

        assertTrue(body.contains("<!DOCTYPE html>"), "rendered body should be the HTML template");
        assertFalse(body.contains("%s"), "all placeholders should be filled");
    }
}
