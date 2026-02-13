package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.IssuedTokens;
import io.routepickapi.dto.auth.kakao.KakaoTokenResponse;
import io.routepickapi.dto.auth.kakao.KakaoUserResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserAuthProvider;
import io.routepickapi.entity.user.UserIdentity;
import io.routepickapi.entity.user.UserIdentityProvider;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserIdentityRepository;
import io.routepickapi.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class KakaoOAuthService {

    private static final String AUTH_BASE_URL = "https://kauth.kakao.com";
    private static final String API_BASE_URL = "https://kapi.kakao.com";

    private final RestClient authClient = RestClient.create(AUTH_BASE_URL);
    private final RestClient apiClient = RestClient.create(API_BASE_URL);

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final AuthService authService;

    @Value("${kakao.oauth.client-id:}")
    private String clientId;

    @Value("${kakao.oauth.client-secret:}")
    private String clientSecret;

    @Value("${kakao.oauth.redirect-uri:}")
    private String redirectUri;

    @Value("${kakao.oauth.logout-redirect-uri:}")
    private String logoutRedirectUri;

    @Value("${kakao.oauth.admin-key:}")
    private String adminKey;

    public String buildAuthorizeUrl(String state) {
        validateConfig();

        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(AUTH_BASE_URL + "/oauth/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri);

        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }

        return builder.build(true).toUriString();
    }

    public String buildLogoutUrl() {
        validateLogoutConfig();

        return UriComponentsBuilder
            .fromHttpUrl(AUTH_BASE_URL + "/oauth/logout")
            .queryParam("client_id", clientId)
            .queryParam("logout_redirect_uri", logoutRedirectUri)
            .build(true)
            .toUriString();
    }

    public IssuedTokens login(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INVALID_INPUT, "인가 코드가 필요합니다.");
        }

        validateConfig();

        KakaoTokenResponse tokenResponse = requestToken(code);
        if (tokenResponse == null || tokenResponse.accessToken() == null
            || tokenResponse.accessToken().isBlank()) {
            throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 토큰 응답이 비어있습니다.");
        }

        KakaoUserResponse userResponse = requestUser(tokenResponse.accessToken());
        if (userResponse == null || userResponse.id() == null) {
            throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 사용자 정보를 확인할 수 없습니다.");
        }

        String providerUserId = String.valueOf(userResponse.id());
        Optional<UserIdentity> existing = userIdentityRepository
            .findByProviderAndProviderUserId(UserIdentityProvider.KAKAO, providerUserId);

        if (existing.isPresent()) {
            User linkedUser = existing.get().getUser();
            if (linkedUser.getStatus() == UserStatus.DELETED) {
                userIdentityRepository.delete(existing.get());
            } else {
                return authService.issueTokensForUser(linkedUser);
            }
        }

        String email = extractVerifiedEmail(userResponse);
        if (email == null || email.isBlank()) {
            throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 이메일 정보가 없습니다.");
        }

        User user = userRepository.findByEmail(email)
            .orElseGet(() -> createNewUser(email, providerUserId));

        authService.validateLoginableUser(user);

        UserIdentity identity = new UserIdentity(user, UserIdentityProvider.KAKAO, providerUserId,
            email);

        try {
            userIdentityRepository.save(identity);
        } catch (DataIntegrityViolationException ex) {
            Optional<UserIdentity> duplicate = userIdentityRepository
                .findByProviderAndProviderUserId(UserIdentityProvider.KAKAO, providerUserId);
            if (duplicate.isPresent()) {
                User duplicateUser = duplicate.get().getUser();
                if (duplicateUser.getStatus() == UserStatus.DELETED) {
                    userIdentityRepository.delete(duplicate.get());
                    userIdentityRepository.save(identity);
                } else {
                    return authService.issueTokensForUser(duplicateUser);
                }
            } else {
                throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 계정 연동에 실패했습니다.");
            }
        }

        return authService.issueTokensForUser(user);
    }

    public void unlinkIfNeeded(Long userId) {
        if (userId == null) {
            return;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));

        if (user.getAuthProvider() != UserAuthProvider.KAKAO) {
            return;
        }

        UserIdentity identity = userIdentityRepository
            .findByUserIdAndProvider(userId, UserIdentityProvider.KAKAO)
            .orElse(null);

        if (identity == null) {
            log.warn("Kakao unlink skipped: identity not found (userId={})", userId);
            return;
        }

        validateUnlinkConfig();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", identity.getProviderUserId());

        try {
            apiClient.post()
                .uri("/v1/user/unlink")
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
            userIdentityRepository.delete(identity);
        } catch (RestClientException ex) {
            log.warn("Kakao unlink request failed (userId={})", userId, ex);
            throw new CustomException(ErrorType.AUTH_OAUTH_UNLINK_FAILED);
        }
    }

    private KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            return authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
        } catch (RestClientException ex) {
            log.warn("Kakao token request failed", ex);
            throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 토큰 발급에 실패했습니다.");
        }
    }

    private KakaoUserResponse requestUser(String accessToken) {
        try {
            return apiClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class);
        } catch (RestClientException ex) {
            log.warn("Kakao user info request failed", ex);
            throw new CustomException(ErrorType.AUTH_OAUTH_FAILED, "카카오 사용자 조회에 실패했습니다.");
        }
    }

    private void validateConfig() {
        if (clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "카카오 OAuth 설정이 필요합니다.");
        }
    }

    private void validateLogoutConfig() {
        if (clientId == null || clientId.isBlank() || logoutRedirectUri == null
            || logoutRedirectUri.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "카카오 OAuth 로그아웃 설정이 필요합니다.");
        }
    }

    private void validateUnlinkConfig() {
        if (adminKey == null || adminKey.isBlank()) {
            throw new CustomException(ErrorType.COMMON_INTERNAL, "카카오 Admin 키 설정이 필요합니다.");
        }
    }

    private String extractVerifiedEmail(KakaoUserResponse response) {
        if (response.kakaoAccount() == null) {
            return null;
        }

        KakaoUserResponse.KakaoAccount account = response.kakaoAccount();
        boolean valid = Boolean.TRUE.equals(account.isEmailValid());
        boolean verified = Boolean.TRUE.equals(account.isEmailVerified());
        if (!valid || !verified) {
            throw new CustomException(ErrorType.AUTH_OAUTH_EMAIL_NOT_VERIFIED);
        }
        return account.email();
    }

    private User createNewUser(String email, String providerUserId) {
        String nickname = buildUniqueNickname(providerUserId);
        User user = new User(email, null, nickname, UserAuthProvider.KAKAO);
        user.markProfileIncomplete();
        user.activate();
        return userRepository.save(user);
    }

    private String buildUniqueNickname(String providerUserId) {
        String base = "kakao_" + providerUserId;
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByNickname(candidate)) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = 40 - suffixText.length();
            String trimmedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) :
                base;
            candidate = trimmedBase + suffixText;
        }
        return candidate;
    }
}
