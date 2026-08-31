package com.jiawa.lyw.identity.api;

import com.jiawa.lyw.identity.application.IdentityApplicationService;
import com.jiawa.lyw.identity.domain.SessionTokens;
import com.jiawa.lyw.identity.infrastructure.IdentityProperties;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.Util.MailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Slf4j
@RequestMapping("/web/identity")
public class IdentityController {
    private final IdentityApplicationService identity;
    private final IdentityProperties properties;

    public IdentityController(IdentityApplicationService identity, IdentityProperties properties) {
        this.identity = identity;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<CommonResp<Void>> register(@Valid @RequestBody LoginRequest request) {
        identity.register(request.email(), request.password());
        return emptyResponse();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<CommonResp<Void>> verifyEmail(@Valid @RequestBody TokenRequest request) {
        identity.verifyEmail(request.token());
        return emptyResponse();
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<CommonResp<Void>> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        try {
            identity.requestPasswordReset(request.email());
        } catch (MailDeliveryException ignored) {
            // 事务代理已回滚；公开结果仍与不存在邮箱相同，不泄露账户存在性。
            log.warn("密码重置邮件发送失败，请检查邮件服务状态");
        }
        return emptyResponse();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<CommonResp<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        identity.resetPassword(request.token(), request.newPassword());
        return emptyResponse();
    }

    @PostMapping("/login")
    public ResponseEntity<CommonResp<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(identity.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<CommonResp<TokenResponse>> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        return tokenResponse(identity.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<CommonResp<Void>> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        identity.logout(refreshToken);
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(properties.secureCookie()).sameSite("Lax").path("/").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(new CommonResp<>());
    }

    private ResponseEntity<CommonResp<TokenResponse>> tokenResponse(SessionTokens tokens) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                .httpOnly(true).secure(properties.secureCookie()).sameSite("Lax").path("/")
                .maxAge(properties.refreshTokenTtl()).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CommonResp<>(new TokenResponse(tokens.accessToken(), tokens.accessExpiresAt())));
    }

    private ResponseEntity<CommonResp<Void>> emptyResponse() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(new CommonResp<>());
    }

    public record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }

    public record TokenRequest(@NotBlank @Size(max = 43) String token) {
        @Override public String toString() { return "TokenRequest[redacted]"; }
    }

    public record EmailRequest(@Email @NotBlank @Size(max = 254) String email) {
        @Override public String toString() { return "EmailRequest[redacted]"; }
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 43) String token, @NotBlank String newPassword) {
        @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
    }

    public record TokenResponse(String accessToken, Instant accessExpiresAt) {
        @Override public String toString() { return "TokenResponse[redacted]"; }
    }
}
