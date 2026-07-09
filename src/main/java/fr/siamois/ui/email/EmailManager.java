package fr.siamois.ui.email;

import jakarta.validation.constraints.Email;

public interface EmailManager {

    String TEXT_HTML = "text/html; charset=UTF-8";

    /**
     * Send an e-mail with the given MIME type (e.g. {@link #TEXT_HTML}).
     *
     * @param to       the recipient address
     * @param subject  the e-mail subject
     * @param body     the e-mail body, encoded according to {@code mimeType}
     * @param mimeType the content type of the body
     */
    void sendEmail(@Email String to, String subject, String body, String mimeType);

}
