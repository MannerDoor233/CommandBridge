// SPDX-License-Identifier: MIT

package com.haavk.relinkplugins.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Crypto for CB plugin: RSA-4096 OAEP-SHA512 decrypt.
 * Keys loaded from ~/.hermes/keys/.
 * (ML-DSA removed — Paper classloader incompatible with shaded BouncyCastle.)
 */
public class CryptoUtil {

    private static final Logger LOG = Logger.getLogger("CryptoUtil");
    private static final String KEY_DIR = System.getProperty("user.home") + "/.hermes/keys/";

    private PrivateKey rsaPrivate;
    private boolean ready = false;

    public CryptoUtil() {
        try {
            loadKeys();
            ready = true;
            LOG.info("CryptoUtil ready: RSA");
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "CryptoUtil init failed", e);
        }
    }

    public boolean isReady() { return ready; }

    // ── Init ────────────────────────────────────────────────

    private void loadKeys() throws Exception {
        // Load RSA private key — prefer pre-decrypted .der, fallback to .enc
        File derFile = new File(KEY_DIR + "rsa_private.der");
        if (derFile.exists()) {
            byte[] derKey = readFile(KEY_DIR + "rsa_private.der");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            rsaPrivate = kf.generatePrivate(new PKCS8EncodedKeySpec(derKey));
            LOG.info("RSA private key loaded from rsa_private.der");
        } else {
            byte[] encData = readFile(KEY_DIR + "rsa_private.enc");
            String meta = new String(readFile(KEY_DIR + "aes_key.enc"), StandardCharsets.UTF_8);
            String totpSecret = new String(readFile(KEY_DIR + "totp_secret.txt"), StandardCharsets.UTF_8).trim();

            String kdfInfo = extractJsonStr(meta, "kdf_info");
            String nonceB64 = extractJsonStr(meta, "nonce");
            if (kdfInfo == null || nonceB64 == null) throw new RuntimeException("Bad aes_key.enc");

            byte[] aesKey = hkdfSha512(
                totpSecret.getBytes(StandardCharsets.US_ASCII),
                kdfInfo.getBytes(StandardCharsets.UTF_8),
                32
            );

            ByteBuffer buf = ByteBuffer.wrap(encData);
            int nonceLen = buf.getInt();
            byte[] nonce = new byte[nonceLen];
            buf.get(nonce);
            byte[] ct = new byte[buf.remaining()];

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] derKey = c.doFinal(ct);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            rsaPrivate = kf.generatePrivate(new PKCS8EncodedKeySpec(derKey));
            LOG.info("RSA private key decrypted from rsa_private.enc");
        }
    }

    // ── RSA Decrypt ─────────────────────────────────────────

    public String rsaDecrypt(String b64) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-512AndMGF1Padding");
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
            "SHA-512", "MGF1", MGF1ParameterSpec.SHA512, PSource.PSpecified.DEFAULT);
        c.init(Cipher.DECRYPT_MODE, rsaPrivate, oaepSpec);
        return new String(c.doFinal(Base64.getDecoder().decode(b64)), StandardCharsets.UTF_8);
    }

    // ── Helpers ─────────────────────────────────────────────

    private byte[] readFile(String p) throws IOException {
        try (FileInputStream f = new FileInputStream(p)) {
            return f.readAllBytes();
        }
    }

    private String extractJsonStr(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
        ).matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    private byte[] hkdfSha512(byte[] ikm, byte[] info, int len) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        byte[] salt = new byte[64];
        mac.init(new SecretKeySpec(salt, "HmacSHA512"));
        byte[] prk = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(prk, "HmacSHA512"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        for (int i = 1; out.size() < len; i++) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(t);
            b.write(info);
            b.write(i);
            t = mac.doFinal(b.toByteArray());
            out.write(t);
        }
        byte[] result = new byte[len];
        System.arraycopy(out.toByteArray(), 0, result, 0, len);
        return result;
    }
}
