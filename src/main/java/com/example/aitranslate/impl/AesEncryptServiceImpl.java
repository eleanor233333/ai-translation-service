package com.example.aitranslate.impl;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AES-256 encryption / decryption for MQ message payloads.
 *
 * All text content is encrypted before being placed on SofaMQ and
 * decrypted by consumers before processing. Keys are managed via
 * the team's KMS (Key Management Service).
 *
 * Implementation provided by team infrastructure.
 */
@Component
public class AesEncryptServiceImpl {

    public String encrypt(String text) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public String decrypt(String text) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public List<String> encryptForStringList(List<String> texts) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public List<String> decryptForStringList(List<String> texts) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }
}
