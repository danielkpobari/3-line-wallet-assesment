package com.example.test.common;

import java.util.Locale;

/** Single place that defines how emails are normalised before storage and lookup. */
public final class Emails {

    private Emails() {
    }

    public static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
