package fr.siamois.ui.email;

import jakarta.validation.constraints.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("log-email")
public class LogEmailManager implements EmailManager {

    @Override
    public void sendEmail(@Email String to, String subject, String body) {
        log.info("""
                Email logged :
                Send to : {}
                From : {}
                Subject : {}
                Body :
                {}
                """, to, "no-reply@localhost",subject, body);
    }

    @Override
    public void sendHtmlEmail(@Email String to, String subject, String htmlBody) {
        log.info("""
                HTML email logged :
                Send to : {}
                From : {}
                Subject : {}
                Body :
                {}
                """, to, "no-reply@localhost", subject, htmlBody);
    }

}
