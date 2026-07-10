package fr.siamois.ui.email;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailManagerImplTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailManagerImpl emailManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailManager, "emailSender", "noreply@siamois.fr");
    }

    @Test
    void sendEmail_setsGivenMimeTypeAndEnvelope() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailManager.sendEmail("invitee@example.com", "Invitation", "<p>Bonjour</p>", EmailManager.TEXT_HTML);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertEquals("Invitation", sent.getSubject());
        assertEquals("invitee@example.com", sent.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("noreply@siamois.fr", sent.getFrom()[0].toString());
        assertTrue(sent.getDataHandler().getContentType().contains("text/html"), "MIME type should be the one passed by the caller");
        assertEquals("<p>Bonjour</p>", sent.getContent());
    }
}
