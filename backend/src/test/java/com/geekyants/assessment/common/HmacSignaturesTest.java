package com.geekyants.assessment.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacSignaturesTest {

    private final byte[] body = "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsMatchingSignature() {
        assertThat(HmacSignatures.verify("secret", body, HmacSignatures.sign("secret", body))).isTrue();
    }

    @Test
    void rejectsWrongSecretMissingHeaderAndUnconfiguredSecret() {
        String signature = HmacSignatures.sign("secret", body);
        assertThat(HmacSignatures.verify("other", body, signature)).isFalse();
        assertThat(HmacSignatures.verify("secret", body, null)).isFalse();
        assertThat(HmacSignatures.verify("", body, "sha256=00")).isFalse();
    }

    @Test
    void rejectsTamperedBody() {
        String signature = HmacSignatures.sign("secret", body);
        byte[] tampered = "{\"zen\":\"tampered\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(HmacSignatures.verify("secret", tampered, signature)).isFalse();
    }
}
