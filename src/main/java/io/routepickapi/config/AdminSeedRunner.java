package io.routepickapi.config;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserRole;
import io.routepickapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeedRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.nickname:}")
    private String adminNickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("Admin seed skipped: missing ADMIN_EMAIL");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin seed skipped: missing ADMIN_PASSWORD");
            return;
        }
        if (adminNickname == null || adminNickname.isBlank()) {
            log.info("Admin seed skipped: missing ADMIN_NICKNAME");
            return;
        }

        if (userRepository.existsByEmail(adminEmail.trim().toLowerCase())) {
            log.info("Admin seed skipped: email already exists ({})", adminEmail);
            return;
        }

        User admin = new User(adminEmail.trim().toLowerCase(),
            passwordEncoder.encode(adminPassword), adminNickname.trim());
        admin.activate();
        admin.setRole(UserRole.ADMIN);

        userRepository.save(admin);
        log.info("Admin account created: email={}", admin.getEmail());
    }
}
