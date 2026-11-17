package com.rmrx.marketnexus.exchange.crypto.nobitex;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Signs requests for Nobitex private endpoints (API key authentication).
 * <p>
 * Payload: {@code timestamp + METHOD + pathWithQuery + rawBody}, signed with Ed25519 and encoded as URL-safe Base64.
 * The timestamp is Unix seconds and must be within 30 seconds of the server clock.
 */
public final class NobitexRequestSigner {

    public static final String HEADER_KEY = "Nobitex-Key";
    public static final String HEADER_SIGNATURE = "Nobitex-Signature";
    public static final String HEADER_TIMESTAMP = "Nobitex-Timestamp";

    /** DER prefix that wraps a raw 32-byte Ed25519 seed into a PKCS#8 structure. */
    private static final byte[] ED25519_PKCS8_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");
    private static final int ED25519_SEED_LENGTH = 32;

    private final String apiKey;
    private final PrivateKey privateKey;
    private final Clock clock;

    public NobitexRequestSigner(NobitexCredentials credentials, Clock clock) {
        Objects.requireNonNull(credentials, "credentials");
        this.apiKey = credentials.apiKey();
        this.privateKey = decodePrivateKey(credentials.privateKey());
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns the authentication headers for one request.
     *
     * @param method        HTTP method, e.g. {@code GET}
     * @param pathWithQuery path without host, including the query string, e.g. {@code /market/orders/list?fromId=123}
     * @param rawBody       exact body bytes that will be sent; empty for requests without a body
     */
    public Map<String, String> authenticationHeaders(String method, String pathWithQuery, byte[] rawBody) {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String signature = sign(timestamp, method, pathWithQuery, rawBody);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_KEY, apiKey);
        headers.put(HEADER_SIGNATURE, signature);
        headers.put(HEADER_TIMESTAMP, timestamp);
        return headers;
    }

    String sign(String timestamp, String method, String pathWithQuery, byte[] rawBody) {
        byte[] prefix = (timestamp + method.toUpperCase() + pathWithQuery).getBytes(StandardCharsets.UTF_8);
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        byte[] payload = Arrays.copyOf(prefix, prefix.length + body.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload);
            return Base64.getUrlEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign Nobitex request", e);
        }
    }

    private static PrivateKey decodePrivateKey(String base64Seed) {
        byte[] seed;
        try {
            seed = Base64.getUrlDecoder().decode(base64Seed.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Nobitex private key is not valid URL-safe Base64", e);
        }
        if (seed.length != ED25519_SEED_LENGTH) {
            throw new IllegalArgumentException(
                    "Nobitex private key must decode to " + ED25519_SEED_LENGTH + " bytes, got " + seed.length);
        }
        byte[] pkcs8 = Arrays.copyOf(ED25519_PKCS8_PREFIX, ED25519_PKCS8_PREFIX.length + seed.length);
        System.arraycopy(seed, 0, pkcs8, ED25519_PKCS8_PREFIX.length, seed.length);
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Nobitex private key is not a valid Ed25519 key", e);
        } finally {
            Arrays.fill(seed, (byte) 0);
            Arrays.fill(pkcs8, (byte) 0);
        }
    }
}
