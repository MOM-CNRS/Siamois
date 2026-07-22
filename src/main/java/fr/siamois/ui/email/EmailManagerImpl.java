package fr.siamois.ui.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Primary
@RequiredArgsConstructor
@Profile("!log-email")
public class EmailManagerImpl implements EmailManager {

    @Value("${spring.mail.properties.mail.sender}")
    private String emailSender;

    private final JavaMailSender mailSender;

    @Override
    public void sendEmail(String to, String subject, String body, String mimeType) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(emailSender);
            helper.setTo(to);
            helper.setSubject(subject);
            message.setContent(body, mimeType);
        } catch (MessagingException e) {
            throw new MailPreparationException("Unable to prepare e-mail to " + to, e);
        }
        mailSender.send(message);
    }

}
