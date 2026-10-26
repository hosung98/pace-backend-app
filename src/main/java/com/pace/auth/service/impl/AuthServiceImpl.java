package com.pace.auth.service.impl;

import com.github.f4b6a3.ulid.UlidCreator;
import com.pace.common.exception.BusinessException;
import com.pace.common.util.TokenHash;
import com.pace.user.entity.*;
import com.pace.user.repository.*;
import com.pace.auth.dto.*;
import com.pace.auth.entity.*;
import com.pace.auth.mapper.AuthLockMapper;
import com.pace.auth.provider.GoogleVerifier;
import com.pace.auth.provider.AppleVerifier;
import com.pace.auth.repository.*;
import com.pace.auth.service.AuthService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthServiceImpl implements AuthService {
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

    public AuthServiceImpl(UserRepository users, SettingsRepository settings, ProviderRepository providers,
                       TokenRepository tokens, AuthLockMapper locks, TransactionTemplate tx,
                       JwtEncoder encoder, GoogleVerifier google, AppleVerifier apple) {
        this.users = users;
        this.settings = settings;
        this.providers = providers;
        this.tokens = tokens;
        this.locks = locks;
        this.tx = tx;
        this.encoder = encoder;
        this.google = google;
        this.apple = apple;
    }

    public AuthTokenResponse login(String idToken) {
        var identity = google.verify(idToken);
        return loginIdentity("google", identity.subject(), identity.name());
    }

    public AuthTokenResponse loginApple(String idToken, String nonce, String nickname) {
        if (nickname != null && (nickname.strip().length() < 2 || nickname.length() > 30)) {
            throw BusinessException.invalid("닉네임은 공백을 제외하고 2~30자여야 합니다.");
        }
        var identity = apple.verify(idToken, nonce);
        return loginIdentity("apple", identity.subject(), nickname);
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
                String name = nickname == null ? "러너" : nickname.strip();
                if (name.length() < 2) name = "러너";
                if (name.length() > 30) {
                    int end = Character.isHighSurrogate(name.charAt(29)) ? 29 : 30;
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

    public AuthTokenResponse refresh(String rawToken) {
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
        if (result == null) throw BusinessException.unauthorized();
        return result;
    }

    public void logout(String publicId, String rawToken) {
        tx.executeWithoutResult(status -> {
            User user = users.lockActive(publicId).orElseThrow(BusinessException::unauthorized);
            tokens.findByTokenHash(TokenHash.sha256(rawToken)).filter(token -> token.getUserId().equals(user.getId()))
                    .ifPresent(token -> tokens.revokeFamily(token.getFamilyId()));
        });
    }

    private User activeUser(long id) {
        User user = users.lockById(id).orElseThrow(BusinessException::unauthorized);
        if (!user.getStatus().equals("active")) throw BusinessException.unauthorized();
        return user;
    }

    private AuthTokenResponse issue(User user, String family, boolean isNew) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("pace-api").subject(user.getPublicId())
                .audience(java.util.List.of("pace-app")).issuedAt(now).expiresAt(now.plusSeconds(1800))
                .id(UlidCreator.getUlid().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String refresh = "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user.getId(), TokenHash.sha256(refresh), family, now.plus(30, ChronoUnit.DAYS)));
        return new AuthTokenResponse(access, refresh, 1800,
                new LoginUserResponse(user.getPublicId(), user.getNickname(), user.getProfileImageUrl()), isNew);
    }

}
