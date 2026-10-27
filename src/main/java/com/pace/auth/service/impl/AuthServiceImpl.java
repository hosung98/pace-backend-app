package com.pace.auth.service.impl;

import com.github.f4b6a3.ulid.UlidCreator;
import com.pace.auth.dto.AuthTokenResponse;
import com.pace.auth.dto.LoginUserResponse;
import com.pace.auth.entity.AuthProvider;
import com.pace.auth.entity.RefreshToken;
import com.pace.auth.mapper.AuthLockMapper;
import com.pace.auth.provider.AppleVerifier;
import com.pace.auth.provider.GoogleVerifier;
import com.pace.auth.repository.ProviderRepository;
import com.pace.auth.repository.TokenRepository;
import com.pace.auth.service.AuthService;
import com.pace.common.exception.BusinessException;
import com.pace.common.util.TokenHash;
import com.pace.user.entity.User;
import com.pace.user.entity.UserSettings;
import com.pace.user.repository.SettingsRepository;
import com.pace.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소셜 로그인/토큰 회전/로그아웃을 한곳에서 관리하는 인증 서비스 구현체입니다.
 * 사용자 생성과 토큰 발급을 같은 트랜잭션 경계로 묶어 일관성을 보장합니다.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LogManager.getLogger(AuthServiceImpl.class);
    private static final String DEFAULT_NICKNAME = "러너";
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 30;
    private static final String ACCESS_TOKEN_AUDIENCE = "pace-app";
    private static final long ACCESS_TOKEN_EXPIRES_SECONDS = 1800L;
    private static final int REFRESH_TOKEN_RANDOM_BYTES = 48;
    private static final long REFRESH_TOKEN_EXPIRES_DAYS = 30L;

    private final UserRepository users;
    private final SettingsRepository settings;
    private final ProviderRepository providers;
    private final TokenRepository tokens;
    private final AuthLockMapper locks;
    private final TransactionTemplate tx;
    private final JwtEncoder encoder;
    private final GoogleVerifier google;
    private final AppleVerifier apple;
    private final SecureRandom random = new SecureRandom();

    /** Google ID 토큰 검증 후 가입/로그인을 수행합니다. */
    public AuthTokenResponse login(String idToken) {
        log.debug("인증 진입: provider=google");
        var identity = google.verify(idToken);
        AuthTokenResponse response = loginIdentity("google", identity.subject(), identity.name());
        log.info("인증 완료: provider=google, userId={}, isNewUser={}", response.user().id(), response.isNewUser());
        return response;
    }

    /** Apple ID 토큰 + nonce 검증 후 가입/로그인을 수행합니다. */
    public AuthTokenResponse loginApple(String idToken, String nonce, String nickname) {
        log.debug("인증 진입: provider=apple, hasNickname={}", nickname != null);
        if (nickname != null && (nickname.strip().length() < NICKNAME_MIN_LENGTH || nickname.length() > NICKNAME_MAX_LENGTH)) {
            log.warn("Apple 로그인 거부: 닉네임 길이 정책 위반");
            throw BusinessException.invalid("닉네임은 공백을 제외하고 2~30자여야 합니다.");
        }
        var identity = apple.verify(idToken, nonce);
        AuthTokenResponse response = loginIdentity("apple", identity.subject(), nickname);
        log.info("인증 완료: provider=apple, userId={}, isNewUser={}", response.user().id(), response.isNewUser());
        return response;
    }

    private AuthTokenResponse loginIdentity(String providerName, String subject, String nickname) {
        return tx.execute(status -> {
            // 기존 Google 잠금 키를 유지해 구·신 버전이 함께 실행될 때도 가입을 직렬화합니다.
            if (providerName.equals("google")) locks.lockGoogleSubject(subject);
            else locks.lockSocialSubject(providerName, subject);
            var provider = providers.findByProviderAndProviderUserId(providerName, subject);
            boolean isNew = provider.isEmpty();
            User user;
            if (isNew) {
                String name = nickname == null ? DEFAULT_NICKNAME : nickname.strip();
                if (name.length() < NICKNAME_MIN_LENGTH) name = DEFAULT_NICKNAME;
                if (name.length() > NICKNAME_MAX_LENGTH) {
                    int end = Character.isHighSurrogate(name.charAt(NICKNAME_MAX_LENGTH - 1))
                            ? NICKNAME_MAX_LENGTH - 1 : NICKNAME_MAX_LENGTH;
                    name = name.substring(0, end);
                }
                user = users.saveAndFlush(new User(name));
                settings.save(new UserSettings(user.getId()));
                providers.save(new AuthProvider(user.getId(), providerName, subject));
            } else {
                user = activeUser(provider.get().getUserId());
                provider.get().login();
            }
            return issue(user, UlidCreator.getUlid().toString(), isNew);
        });
    }

    /** 리프레시 토큰을 회전하며 재사용이 감지되면 같은 family를 즉시 폐기합니다. */
    public AuthTokenResponse refresh(String rawToken) {
        log.debug("토큰 재발급 진입");
        AuthTokenResponse result = tx.execute(status -> {
            var found = tokens.findUserId(TokenHash.sha256(rawToken));
            if (found.isEmpty()) return null;
            User user = activeUser(found.get());
            // 잠금을 얻은 다음 토큰을 읽어 동시 회전 요청도 재사용으로 감지합니다.
            var token = tokens.findByTokenHash(TokenHash.sha256(rawToken)).orElseThrow(BusinessException::unauthorized);
            if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
                tokens.revokeFamily(token.getFamilyId());
                return null;
            }
            token.revoke();
            return issue(user, token.getFamilyId(), false);
        });
        // 재사용 탐지에 따른 폐기를 커밋한 뒤 인증 오류를 반환합니다.
        if (result == null) {
            log.warn("토큰 재발급 실패: 만료 또는 재사용 탐지");
            throw BusinessException.unauthorized();
        }
        log.info("토큰 재발급 완료: userId={}", result.user().id());
        return result;
    }

    /** 전달된 갱신 토큰 family 전체를 폐기해 세션을 로그아웃 처리합니다. */
    public void logout(String publicId, String rawToken) {
        log.debug("로그아웃 진입: userId={}", publicId);
        tx.executeWithoutResult(status -> {
            User user = users.lockActive(publicId).orElseThrow(BusinessException::unauthorized);
            tokens.findByTokenHash(TokenHash.sha256(rawToken)).filter(token -> token.getUserId().equals(user.getId()))
                    .ifPresent(token -> tokens.revokeFamily(token.getFamilyId()));
        });
        log.info("로그아웃 완료: userId={}", publicId);
    }

    /** 활성 사용자만 인증 성공으로 인정합니다. */
    private User activeUser(long id) {
        User user = users.lockById(id).orElseThrow(BusinessException::unauthorized);
        if (!user.getStatus().equals("active")) throw BusinessException.unauthorized();
        return user;
    }

    /** 액세스/리프레시 토큰을 새로 발급하고 해시된 리프레시 토큰만 저장합니다. */
    private AuthTokenResponse issue(User user, String family, boolean isNew) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("pace-api").subject(user.getPublicId())
                .audience(List.of(ACCESS_TOKEN_AUDIENCE))
                .issuedAt(now).expiresAt(now.plusSeconds(ACCESS_TOKEN_EXPIRES_SECONDS))
                .id(UlidCreator.getUlid().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[REFRESH_TOKEN_RANDOM_BYTES];
        random.nextBytes(bytes);
        String refresh = "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user.getId(), TokenHash.sha256(refresh), family,
                now.plus(REFRESH_TOKEN_EXPIRES_DAYS, ChronoUnit.DAYS)));
        return new AuthTokenResponse(access, refresh, (int) ACCESS_TOKEN_EXPIRES_SECONDS,
                new LoginUserResponse(user.getPublicId(), user.getNickname(), user.getProfileImageUrl()), isNew);
    }

}
