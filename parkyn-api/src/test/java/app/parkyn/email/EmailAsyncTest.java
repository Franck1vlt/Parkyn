package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.awaitility.Awaitility.await;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import app.parkyn.IntegrationTest;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest 
public class EmailAsyncTest {

    private static final String RECIPIENT = "test@example.com";
    private static final Map<String, Object> EMAIL_CONTEXT = Map.of("name", "Parkyn", "link", "http://localhost/verify?token=abc");

    @MockitoSpyBean JavaMailSender mailSender;
    @Autowired EmailService emailService;

    // --- Tests ---

    @Test 
    public void sendRunsOnEmailExecutor() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            sent.countDown();
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        emailService.send(RECIPIENT, "Sujet", "test", EMAIL_CONTEXT);

        assertTrue(sent.await(5, TimeUnit.SECONDS), "send non appelé");
        assertTrue(threadName.get().startsWith("email-"), threadName.get());
    }

    @Test 
    public void callerDoesNotWaitForSmtp() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            Thread.sleep(2000);
            sent.countDown();
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        long start = System.nanoTime();
        emailService.send(RECIPIENT, "Sujet", "test", EMAIL_CONTEXT);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis < 500, "send a bloqué pendant " + elapsedMillis + " ms");
        assertTrue(sent.await(5, TimeUnit.SECONDS), "send non appelé");
    }

    @Test 
    public void smtpFailureDoesNotReachCaller(CapturedOutput output) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            throw new MailSendException("SMTP down");
        }).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.send(RECIPIENT, "Sujet", "test", EMAIL_CONTEXT));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "exception SMTP non levée");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(output.getOut().contains("Échec de l'envoi de l'email")));
    }

}
