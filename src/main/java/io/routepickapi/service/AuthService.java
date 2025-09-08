package io.routepickapi.service;

import io.routepickapi.dto.auth.SignUpRequest;
import io.routepickapi.dto.auth.SignUpResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignUpResponse signUp(SignUpRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
        }

        String hash = passwordEncoder.encode(req.password());
        User user = new User(req.email(), hash, req.nickname());
        Long id = userRepository.save(user).getId();

        log.info("User signed up: id={}, email={}", id, user.getEmail());
        return new SignUpResponse(id, user.getEmail(), user.getNickname());
    }
}
