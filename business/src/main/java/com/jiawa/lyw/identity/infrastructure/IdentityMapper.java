package com.jiawa.lyw.identity.infrastructure;

import com.jiawa.lyw.identity.domain.MemberAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface IdentityMapper {
    MemberAccount findAccountByEmailForUpdate(@Param("email") String email);

    void insertPendingAccountIfAbsent(@Param("id") long id, @Param("email") String email, @Param("hash") String hash, @Param("now") Instant now);

    int activateAccount(@Param("memberId") long memberId, @Param("now") Instant now);

    void invalidateOneTimeTokens(@Param("memberId") long memberId, @Param("purpose") OneTimeTokenService.Purpose purpose,
                                 @Param("now") Instant now);

    void insertOneTimeToken(@Param("id") long id, @Param("memberId") long memberId, @Param("email") String email,
                            @Param("purpose") OneTimeTokenService.Purpose purpose, @Param("tokenHash") String tokenHash,
                            @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

    Long findOneTimeTokenMemberId(@Param("tokenHash") String tokenHash, @Param("purpose") OneTimeTokenService.Purpose purpose);

    int consumeOneTimeToken(@Param("tokenHash") String tokenHash, @Param("purpose") OneTimeTokenService.Purpose purpose,
                             @Param("email") String email, @Param("now") Instant now);

    MemberAccount findAccountByIdForUpdate(@Param("memberId") long memberId);

    Long findRefreshMemberId(@Param("tokenHash") String tokenHash);

    int consumeRefreshSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int revokeRefreshSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int revokeMemberSessions(@Param("memberId") long memberId, @Param("now") Instant now);

    int upgradePassword(@Param("memberId") long memberId, @Param("hash") String hash, @Param("now") Instant now);

    void insertRefreshSession(@Param("id") long id, @Param("memberId") long memberId,
                              @Param("tokenHash") String tokenHash, @Param("expiresAt") Instant expiresAt,
                              @Param("now") Instant now);
}
