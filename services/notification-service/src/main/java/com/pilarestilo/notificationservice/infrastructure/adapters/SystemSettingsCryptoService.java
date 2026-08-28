package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.shared.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/GCM, same scheme and key derivation as the monolith's {@code SystemSettingsCryptoService}.
 * Ported verbatim so a secret encrypted by the admin panel decrypts identically here. Pure — no
 * database — keyed off {@code SYSTEM_SETTINGS_CRYPTO_SECRET} (or {@code JWT_SECRET}).
 */
@Service
public class SystemSettingsCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SystemSettingsCryptoService(
            @Value("${app.system-settings.crypto-secret:${JWT_SECRET:change-this-secret}}") String rawSecret
    ) {
        this.keySpec = new SecretKeySpec(deriveKey(rawSecret), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new DomainException("Secret cannot be blank");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception _) {
            throw new DomainException("Could not encrypt secret");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return "";
        }
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedText);
            if (packed.length <= IV_LENGTH_BYTES) {
                throw new DomainException("Invalid encrypted secret");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] cipherText = new byte[packed.length - IV_LENGTH_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(packed, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception _) {
            throw new DomainException("Could not decrypt secret");
        }
    }

    private static byte[] deriveKey(String secret) {
        try {
            String source = (secret == null || secret.isBlank()) ? "change-this-secret" : secret.trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not derive crypto key", ex);
        }
    }
}
