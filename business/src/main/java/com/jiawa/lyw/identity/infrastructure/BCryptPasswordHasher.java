package com.jiawa.lyw.identity.infrastructure;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiawa.lyw.identity.domain.PasswordHasher;
import com.jiawa.lyw.identity.domain.IdentityException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BCryptPasswordHasher implements PasswordHasher {
    private static final Pattern BCRYPT_FORMAT = Pattern.compile("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String rawPassword) {
        if (!isHashable(rawPassword)) {
            throw new IdentityException("密码不能为空且 UTF-8 编码不得超过 72 字节");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public PasswordCheck verify(String rawPassword, String storedHash, String algorithm) {
        if (!isHashable(rawPassword) || storedHash == null) {
            return new PasswordCheck(false, false);
        }
        if ("BCRYPT".equals(algorithm)) {
            if (!BCRYPT_FORMAT.matcher(storedHash).matches()) {
                return new PasswordCheck(false, false);
            }
            boolean matches = encoder.matches(rawPassword, storedHash);
            return new PasswordCheck(matches, matches && encoder.upgradeEncoding(storedHash));
        }
        // 显式历史兼容层：只验证旧格式，不生成供新账户保存的旧格式凭据。
        if ("LEGACY_DOUBLE_MD5".equals(algorithm)) {
            String candidate = DigestUtil.md5Hex(DigestUtil.md5Hex(rawPassword));
            boolean matches = MessageDigest.isEqual(
                    candidate.getBytes(StandardCharsets.US_ASCII),
                    storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
            return new PasswordCheck(matches, matches);
        }
        return new PasswordCheck(false, false);
    }

    private boolean isHashable(String rawPassword) {
        return rawPassword != null && !rawPassword.isEmpty()
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_UTF8_BYTES;
    }
}
