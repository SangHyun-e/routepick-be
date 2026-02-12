package io.routepickapi.service;

import io.routepickapi.entity.user.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
public class SmtpEmailSenderService implements EmailSenderService {

    private static final String SUBJECT = "[RoutePick] 이메일 인증 코드";
    private static final String RESET_SUBJECT = "[RoutePick] 비밀번호 재설정 코드";

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public SmtpEmailSenderService(
        JavaMailSender mailSender,
        @Value("${smtp.from-email:${spring.mail.username:}}") String fromEmail
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendVerificationCode(User user, String code, long ttlSeconds) {
        long ttlMinutes = Math.max(1, Duration.ofSeconds(ttlSeconds).toMinutes());
        String body = String.format("인증 코드는 %s 입니다.%n유효시간은 %d분입니다.", code,
            ttlMinutes);
        sendTextMail(user.getEmail(), SUBJECT, body);
        log.info("SMTP verification email sent (email={}, ttlMinutes={})", user.getEmail(),
            ttlMinutes);
    }

    @Override
    public void sendPasswordResetCode(User user, String code, long ttlSeconds) {
        long ttlMinutes = Math.max(1, Duration.ofSeconds(ttlSeconds).toMinutes());
        String body = String.format("비밀번호 재설정 코드는 %s 입니다.%n유효시간은 %d분입니다.", code,
            ttlMinutes);
        sendTextMail(user.getEmail(), RESET_SUBJECT, body);
        log.info("SMTP password reset email sent (email={}, ttlMinutes={})", user.getEmail(),
            ttlMinutes);
    }

    private void sendTextMail(String to, String subject, String body) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("SMTP from email is not configured");
        }
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to send email", ex);
        }
    }
}
