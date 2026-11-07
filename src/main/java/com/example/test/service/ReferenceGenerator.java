package com.example.test.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Builds human-readable, roughly time-ordered transaction references, e.g. {@code TRF20261106221530A1B2C3D4E5F6}:
 * a type prefix, a UTC timestamp, then 12 random hex characters.
 */
@Component
public class ReferenceGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SecureRandom random = new SecureRandom();

    public String next(String prefix) {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        return prefix + ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP)
                + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
