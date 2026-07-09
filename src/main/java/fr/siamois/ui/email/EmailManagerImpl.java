package fr.siamois.ui.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Profile("!log-email")
public class EmailManagerImpl implements EmailManager {

    private static final Logger log = LoggerFactory.getLogger(EmailManagerImpl.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String smtpUser;

    @Override
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtpUser);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        logSentEmail(to, subject);
    }

    private static void logSentEmail(String to, String subject) {
        log.info("Email sent do {} with subject {}", to, subject);
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(smtpUser);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
        } catch (MessagingException e) {
            throw new MailPreparationException("Failed to prepare HTML email", e);
        }
        mailSender.send(message);
        logSentEmail(to, subject);
    }
}
