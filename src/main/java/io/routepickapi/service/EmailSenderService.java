package io.routepickapi.service;

import io.routepickapi.entity.user.User;

public interface EmailSenderService {

    void sendVerificationCode(User user, String code, long ttlSeconds);

    void sendPasswordResetCode(User user, String code, long ttlSeconds);
}
