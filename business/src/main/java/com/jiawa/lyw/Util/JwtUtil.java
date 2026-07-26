package com.jiawa.lyw.Util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.crypto.GlobalBouncyCastleProvider;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class JwtUtil {
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String JWT_SECRET_ENV = "JWT_SECRET";

    private JwtUtil() {
    }

    public static String createLoginToken(Map<String, Object> map) {
        return createToken(map, 24 * 60);
    }

    public static String createToken(
            Map<String, Object> map,
            Integer expMinutes
    ) {
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        DateTime now = DateTime.now();
        DateTime expTime = now.offsetNew(DateField.MINUTE, expMinutes);
        Map<String, Object> payload = new HashMap<>();
        payload.put(JWTPayload.ISSUED_AT, now);
        payload.put(JWTPayload.EXPIRES_AT, expTime);
        payload.put(JWTPayload.NOT_BEFORE, now);
        payload.putAll(map);
        return JWTUtil.createToken(payload, signingKey());
    }

    public static boolean validate(String token) {
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        JWT jwt = JWTUtil.parseToken(token).setKey(signingKey());
        return jwt.validate(0);
    }

    public static JSONObject getJSONObject(String token) {
        GlobalBouncyCastleProvider.setUseBouncyCastle(false);
        JWT jwt = JWTUtil.parseToken(token).setKey(signingKey());
        JSONObject payloads = jwt.getPayloads();
        payloads.remove(JWTPayload.ISSUED_AT);
        payloads.remove(JWTPayload.EXPIRES_AT);
        payloads.remove(JWTPayload.NOT_BEFORE);
        return payloads;
    }

    private static byte[] signingKey() {
        String value = System.getenv(JWT_SECRET_ENV);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + JWT_SECRET_ENV
            );
        }
        byte[] key = value.getBytes(StandardCharsets.UTF_8);
        if (key.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must contain at least 32 UTF-8 bytes"
            );
        }
        return key;
    }
}
