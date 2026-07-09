package fr.siamois.ui.email;

import jakarta.validation.constraints.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogEmailManager implements EmailManager {

    @Value("${spring.mail.properties.mail.sender}")
    private String emailSender;

    @Override
    public void sendEmail(@Email String to, String subject, String body, String mimeType) {
        log.info("""
                Email sent :
                From : {}
                To : {}
                Subject : {}
                Content-type : {}
                Body :
                {}
                """, emailSender, to, subject, mimeType, body);
    }

}
