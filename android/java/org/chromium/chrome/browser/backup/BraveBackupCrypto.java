/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.backup;

import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles AES-256-GCM encryption and decryption with PBKDF2 key derivation for .bravebak containers.
 */
public class BraveBackupCrypto {
    private static final byte[] MAGIC = "BRAVEBAK".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 0x01;

    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12; // Standard 96-bit nonce for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;

    // Fallback default passphrase when user leaves custom password empty
    private static final String DEFAULT_CONTAINER_KEY = "brave-android-local-backup-storage-key";

    public static OutputStream createEncryptedOutputStream(OutputStream targetOut, String password)
            throws IOException, GeneralSecurityException {
        String effectivePass = TextUtils.isEmpty(password) ? DEFAULT_CONTAINER_KEY : password;

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);

        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        SecretKey key = deriveKey(effectivePass, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        // Write header: MAGIC (8 bytes) + VERSION (1 byte) + SALT (16 bytes) + IV (12 bytes)
        targetOut.write(MAGIC);
        targetOut.write(VERSION);
        targetOut.write(salt);
        targetOut.write(iv);

        return new CipherOutputStream(targetOut, cipher);
    }

    public static InputStream createDecryptedInputStream(InputStream sourceIn, String password)
            throws IOException, GeneralSecurityException {
        String effectivePass = TextUtils.isEmpty(password) ? DEFAULT_CONTAINER_KEY : password;

        byte[] magicBuf = new byte[MAGIC.length];
        readFully(sourceIn, magicBuf);
        if (!Arrays.equals(magicBuf, MAGIC)) {
            throw new IOException("Invalid backup format: missing BRAVEBAK magic header");
        }

        int version = sourceIn.read();
        if (version != VERSION) {
            throw new IOException("Unsupported backup container version: " + version);
        }

        byte[] salt = new byte[SALT_BYTES];
        readFully(sourceIn, salt);

        byte[] iv = new byte[IV_BYTES];
        readFully(sourceIn, iv);

        SecretKey key = deriveKey(effectivePass, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return new CipherInputStream(sourceIn, cipher);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = in.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                throw new IOException("Unexpected end of stream while reading container header");
            }
            offset += count;
        }
    }
}
