package io.routepickapi.service;

import io.routepickapi.entity.user.User;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@Slf4j
@Component
@Profile("prod")
public class SesEmailSender implements EmailSender {

    private static final String SUBJECT = "[RoutePick] 이메일 인증 코드";

    private final SesClient sesClient;
    private final String fromEmail;

    public SesEmailSender(
        @Value("${aws.region:ap-northeast-2}") String region,
        @Value("${aws.ses.from-email}") String fromEmail
    ) {
        this.fromEmail = fromEmail;
        this.sesClient = SesClient.builder()
            .region(Region.of(region))
            .build();
    }

    @Override
    public void sendVerificationCode(User user, String code, long ttlSeconds) {
        long ttlMinutes = Math.max(1, Duration.ofSeconds(ttlSeconds).toMinutes());
        String body = String.format("인증 코드는 %s 입니다.%n유효시간은 %d분입니다.", code,
            ttlMinutes);

        SendEmailRequest request = SendEmailRequest.builder()
            .source(fromEmail)
            .destination(Destination.builder().toAddresses(user.getEmail()).build())
            .message(Message.builder()
                .subject(Content.builder().data(SUBJECT).charset("UTF-8").build())
                .body(Body.builder()
                    .text(Content.builder().data(body).charset("UTF-8").build())
                    .build())
                .build())
            .build();

        sesClient.sendEmail(request);
        log.info("SES verification email sent (email={}, ttlMinutes={})", user.getEmail(),
            ttlMinutes);
    }

    @PreDestroy
    void close() {
        sesClient.close();
    }
}
