package com.payflow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSignatureUtilTest {

    private static final String SECRET = "whsec_test_secret_key_1234567890";
    private static final String PAYLOAD = "{\"eventId\":\"evt_123\",\"status\":\"CAPTURED\",\"amount\":100.00}";

    @Test
    @DisplayName("Should generate consistent deterministic HMAC-SHA256 signature")
    void shouldGenerateSignature() {
        String sig1 = HmacSignatureUtil.calculateSignature(SECRET, PAYLOAD);
        String sig2 = HmacSignatureUtil.calculateSignature(SECRET, PAYLOAD);

        assertThat(sig1).isNotNull().isNotEmpty().isEqualTo(sig2);
    }

    @Test
    @DisplayName("Should successfully verify valid signature")
    void shouldVerifyValidSignature() {
        String signature = HmacSignatureUtil.calculateSignature(SECRET, PAYLOAD);
        boolean isValid = HmacSignatureUtil.verifySignature(SECRET, PAYLOAD, signature);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject tampered payload or incorrect signature")
    void shouldRejectTamperedPayload() {
        String signature = HmacSignatureUtil.calculateSignature(SECRET, PAYLOAD);

        boolean tamperedPayloadValid = HmacSignatureUtil.verifySignature(SECRET, PAYLOAD + " ", signature);
        boolean tamperedSecretValid = HmacSignatureUtil.verifySignature("wrong_secret", PAYLOAD, signature);
        boolean tamperedSigValid = HmacSignatureUtil.verifySignature(SECRET, PAYLOAD, signature + "a");

        assertThat(tamperedPayloadValid).isFalse();
        assertThat(tamperedSecretValid).isFalse();
        assertThat(tamperedSigValid).isFalse();
    }

    @Test
    @DisplayName("Should handle null inputs safely")
    void shouldHandleNullsSafely() {
        assertThatThrownBy(() -> HmacSignatureUtil.calculateSignature(null, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HmacSignatureUtil.calculateSignature(SECRET, null, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(HmacSignatureUtil.verifySignature(null, PAYLOAD, "sig")).isFalse();
        assertThat(HmacSignatureUtil.verifySignature(SECRET, null, "sig")).isFalse();
        assertThat(HmacSignatureUtil.verifySignature(SECRET, PAYLOAD, null)).isFalse();
        assertThat(HmacSignatureUtil.verifySignature(SECRET, "1712234000", null, "sig")).isFalse();
    }

    @Test
    @DisplayName("Should sign and verify with timestamp header")
    void shouldSignAndVerifyWithTimestamp() {
        String timestamp = "1712234000";
        String signature = HmacSignatureUtil.calculateSignature(SECRET, timestamp, PAYLOAD);

        assertThat(signature).isNotNull().isNotEmpty();
        assertThat(HmacSignatureUtil.verifySignature(SECRET, timestamp, PAYLOAD, signature)).isTrue();
        assertThat(HmacSignatureUtil.verifySignature(SECRET, "1712234001", PAYLOAD, signature)).isFalse();
    }
}
