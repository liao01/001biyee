package com.jiawa.lyw.identity.domain;

import java.time.Instant;

public record MemberAccount(
        long id,
        String email,
        Instant emailVerifiedAt,
        String passwordHash,
        String passwordAlgorithm,
        String name,
        AccountStatus accountStatus) {
    public enum AccountStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        EMAIL_BINDING_REQUIRED,
        DISABLED
    }

    @Override
    public String toString() {
        return "MemberAccount[id=" + id + ", status=" + accountStatus + "]";
    }
}
