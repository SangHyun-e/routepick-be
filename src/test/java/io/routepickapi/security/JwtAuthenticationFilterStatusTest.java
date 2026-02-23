package io.routepickapi.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.routepickapi.common.error.ErrorType;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNA==")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationFilterStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void suspendedUserTokenIsRejected() throws Exception {
        User user = new User("suspended-token@example.com", "hash", "suspended-token");
        user.activate();
        user.block();
        User saved = userRepository.save(user);

        String token = jwtProvider.generateAccessToken(saved.getId(), saved.getEmail());

        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorType.USER_STATUS_INACTIVE.code));
    }
}
