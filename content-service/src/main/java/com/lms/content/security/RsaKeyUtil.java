package com.lms.content.security;

import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * Utility for loading RSA public keys from PEM files.
 *
 * <p>The PEM format strips the header/footer lines and decodes the Base64 body
 * to get the DER-encoded public key bytes, which {@link KeyFactory} can parse.
 */
public final class RsaKeyUtil {

    private RsaKeyUtil() {}

    /**
     * Loads an RSA public key from a PEM-encoded {@link Resource}.
     *
     * <p>Expected format:
     * <pre>
     * -----BEGIN PUBLIC KEY-----
     * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
     * -----END PUBLIC KEY-----
     * </pre>
     */
    public static RSAPublicKey loadPublicKey(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream()))) {

            String pem = reader.lines()
                .filter(line -> !line.startsWith("-----"))
                .collect(Collectors.joining());

            byte[] keyBytes = Base64.getDecoder().decode(pem);

            try {
                KeyFactory kf  = KeyFactory.getInstance("RSA");
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                return (RSAPublicKey) kf.generatePublic(spec);
            } catch (Exception e) {
                throw new IOException("Failed to parse RSA public key: " + e.getMessage(), e);
            }
        }
    }
}
