package com.geekyants.assessment.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies "sha256=<hex>" HMAC signatures as sent by GitHub webhooks and the evaluation runner. */
public final class HmacSignatures {

    private static final String PREFIX = "sha256=";

    private HmacSignatures() {
    }

    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String secret, byte[] body, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
