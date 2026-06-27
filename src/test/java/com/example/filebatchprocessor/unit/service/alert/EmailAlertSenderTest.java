package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import com.example.filebatchprocessor.service.alert.EmailAlertSender;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailAlertSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    @SuppressWarnings("unchecked")
    void sendsEmailToConfiguredRecipients() throws Exception {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost("localhost");
        mail.setPort(greenMail.getSmtp().getPort());

        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);

        EmailAlertSender sender = new EmailAlertSender(provider, "alert@from.local", "ops@to.local");
        sender.send(AlertEvent.of("CODE", AlertSeverity.CRITICAL, "the message", Map.of("k", "v")));

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        assertTrue(received[0].getSubject().contains("CRITICAL"));
        assertTrue(received[0].getSubject().contains("CODE"));
        assertTrue(GreenMailUtil.getBody(received[0]).contains("the message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledWhenMailSenderUnavailable() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        EmailAlertSender sender = new EmailAlertSender(provider, "f", "ops@to.local");

        assertFalse(sender.isEnabled());
    }
}
