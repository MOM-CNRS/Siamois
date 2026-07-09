package fr.siamois.ui.email;

import jakarta.validation.constraints.Email;

public interface EmailManager {
    void sendEmail(@Email String to, String subject, String body);
    void sendHtmlEmail(@Email String to, String subject, String htmlBody);
}
