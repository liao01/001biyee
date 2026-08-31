package com.jiawa.lyw.identity.application;

import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.identity.domain.MemberAccount;
import com.jiawa.lyw.identity.domain.MemberProfile;
import com.jiawa.lyw.identity.infrastructure.HttpCurrentMemberProvider;
import com.jiawa.lyw.identity.domain.PasswordHasher;
import com.jiawa.lyw.identity.domain.PasswordPolicy;
import com.jiawa.lyw.identity.domain.SessionTokens;
import com.jiawa.lyw.identity.infrastructure.IdentityMapper;
import com.jiawa.lyw.identity.infrastructure.RefreshSessionService;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.identity.infrastructure.OneTimeTokenService;
import cn.hutool.core.util.IdUtil;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.util.Locale;

public class DefaultIdentityApplicationService implements IdentityApplicationService {
    private final IdentityMapper mapper;
    private final PasswordHasher passwords;
    private final RefreshSessionService sessions;
    private final Clock clock;
    private final OneTimeTokenService oneTimeTokens;
    private final IdentityMailGateway mail;
    private final HttpCurrentMemberProvider currentMember;

    public DefaultIdentityApplicationService(IdentityMapper mapper, PasswordHasher passwords,
                                             RefreshSessionService sessions, Clock clock,
                                             OneTimeTokenService oneTimeTokens, IdentityMailGateway mail,
                                             HttpCurrentMemberProvider currentMember) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.sessions = sessions;
        this.clock = clock;
        this.oneTimeTokens = oneTimeTokens;
        this.mail = mail;
        this.currentMember = currentMember;
    }

    @Override
    public MemberProfile currentMember() { return currentMember.profile(); }

    @Override
    @Transactional
    public SessionTokens login(String email, String rawPassword) {
        MemberAccount account = email == null ? null : mapper.findAccountByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT));
        if (account == null) {
            throw invalidCredentials();
        }
        var checked = passwords.verify(rawPassword, account.passwordHash(), account.passwordAlgorithm());
        if (!checked.matches() || account.accountStatus() == MemberAccount.AccountStatus.DISABLED
                || account.accountStatus() == MemberAccount.AccountStatus.EMAIL_BINDING_REQUIRED) {
            throw invalidCredentials();
        }
        if (account.accountStatus() != MemberAccount.AccountStatus.ACTIVE || account.emailVerifiedAt() == null) {
            throw new IdentityException(IdentityException.Reason.EMAIL_NOT_VERIFIED, "请先完成邮箱验证");
        }
        if (checked.needsUpgrade()) {
            mapper.upgradePassword(account.id(), passwords.hash(rawPassword), clock.instant());
        }
        return sessions.issue(account.id());
    }

    private IdentityException invalidCredentials() {
        return new IdentityException(IdentityException.Reason.UNAUTHENTICATED, "邮箱或密码不正确");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void register(String email, String rawPassword) {
        PasswordPolicy.validateNewPassword(rawPassword);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String hash = passwords.hash(rawPassword);
        long candidateId = IdUtil.getSnowflakeNextId();
        // 唯一键上的原子初始化避免“先查为空再插入”的竞争；已有账户不被该语句改写。
        mapper.insertPendingAccountIfAbsent(candidateId, normalizedEmail, hash, clock.instant());
        MemberAccount account = mapper.findAccountByEmailForUpdate(normalizedEmail);
        if (account == null) {
            throw new IllegalStateException("Identity account initialization failed");
        }
        if (account.accountStatus() != MemberAccount.AccountStatus.PENDING_VERIFICATION) {
            return;
        }
        if (account.id() != candidateId) {
            mapper.upgradePassword(account.id(), hash, clock.instant());
        }
        mail.sendVerificationLink(normalizedEmail, oneTimeTokens.issue(account.id(), normalizedEmail, OneTimeTokenService.Purpose.VERIFY_EMAIL));
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        MemberAccount account = oneTimeTokens.consume(rawToken, OneTimeTokenService.Purpose.VERIFY_EMAIL);
        if (mapper.activateAccount(account.id(), clock.instant()) != 1) {
            throw new IdentityException(IdentityException.Reason.INVALID_TOKEN, "链接已失效，请重新申请");
        }
    }
    @Override
    @Transactional
    public SessionTokens refresh(String rawRefreshToken) {
        return sessions.rotate(rawRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        sessions.revoke(rawRefreshToken);
    }
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void requestPasswordReset(String email) {
        MemberAccount account = mapper.findAccountByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT));
        if (account != null && account.accountStatus() == MemberAccount.AccountStatus.ACTIVE && account.emailVerifiedAt() != null) {
            mail.sendPasswordResetLink(account.email(), oneTimeTokens.issue(account.id(), account.email(), OneTimeTokenService.Purpose.RESET_PASSWORD));
        }
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newRawPassword) {
        PasswordPolicy.validateNewPassword(newRawPassword);
        MemberAccount account = oneTimeTokens.consume(rawToken, OneTimeTokenService.Purpose.RESET_PASSWORD);
        if (account.accountStatus() != MemberAccount.AccountStatus.ACTIVE || account.emailVerifiedAt() == null) {
            throw new IdentityException(IdentityException.Reason.INVALID_TOKEN, "链接已失效，请重新申请");
        }
        mapper.upgradePassword(account.id(), passwords.hash(newRawPassword), clock.instant());
        mapper.revokeMemberSessions(account.id(), clock.instant());
        mapper.invalidateOneTimeTokens(account.id(), OneTimeTokenService.Purpose.RESET_PASSWORD, clock.instant());
    }
}
