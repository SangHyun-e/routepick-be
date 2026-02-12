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
        String html = buildHtmlEmail("이메일 인증 코드", "아래 인증 코드를 입력해주세요.", code,
            ttlMinutes);
        sendMail(user.getEmail(), SUBJECT, body, html);
        log.info("SMTP verification email sent (email={}, ttlMinutes={})", user.getEmail(),
            ttlMinutes);
    }

    @Override
    public void sendPasswordResetCode(User user, String code, long ttlSeconds) {
        long ttlMinutes = Math.max(1, Duration.ofSeconds(ttlSeconds).toMinutes());
        String body = String.format("비밀번호 재설정 코드는 %s 입니다.%n유효시간은 %d분입니다.", code,
            ttlMinutes);
        String html = buildHtmlEmail("비밀번호 재설정", "아래 코드를 입력해 비밀번호를 재설정하세요.", code,
            ttlMinutes);
        sendMail(user.getEmail(), RESET_SUBJECT, body, html);
        log.info("SMTP password reset email sent (email={}, ttlMinutes={})", user.getEmail(),
            ttlMinutes);
    }

    private void sendMail(String to, String subject, String textBody, String htmlBody) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("SMTP from email is not configured");
        }
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    private String buildHtmlEmail(String title, String description, String code,
        long ttlMinutes) {
        return String.format(
            """
                <div style=\"background:#f8fafc;padding:24px;font-family:Arial,sans-serif;color:#0f172a\">\
                  <div style=\"max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;padding:24px;border:1px solid #e2e8f0\">\
                    <h2 style=\"margin:0 0 8px;font-size:20px;\">%s</h2>\
                    <p style=\"margin:0 0 16px;color:#475569;font-size:14px;\">%s</p>\
                    <div style=\"margin:20px 0;padding:16px;border-radius:12px;background:#f1f5f9;text-align:center;\">\
                      <div style=\"font-size:32px;font-weight:700;letter-spacing:6px;\">%s</div>\
                      <div style=\"margin-top:8px;color:#64748b;font-size:12px;\">유효시간 %d분</div>\
                    </div>\
                    <p style=\"margin:0;color:#94a3b8;font-size:12px;\">본 메일은 발신 전용입니다.</p>\
                  </div>\
                </div>\
                """,
            title,
            description,
            code,
            ttlMinutes
        );
    }
}
