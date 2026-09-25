package com.geekyants.assessment.common;

import java.security.SecureRandom;

/**
 * Generates human-readable public IDs such as {@code ASSIGN-7K2M9QX4TB}.
 * Uses Crockford base32 (no I, L, O, U) so IDs are safe to read aloud and paste.
 */
public final class Ids {

    public static final String ASSIGNMENT = "ASSIGN";
    public static final String BLUEPRINT = "BP";
    public static final String PROJECT = "PROJ";
    public static final String REPOSITORY = "REPO";
    public static final String SUBMISSION = "SUB";
    public static final String EVALUATION = "EVAL";
    public static final String CODE_ANALYSIS = "CA";
    public static final String ASSESSMENT = "ASMT";
    public static final String REPORT = "RPT";

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String next(String prefix) {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return prefix + "-" + new String(chars);
    }

    /** Sequential codes used inside a parent, e.g. REQ-001, MCQ-012. */
    public static String sequence(String prefix, int number) {
        return "%s-%03d".formatted(prefix, number);
    }
}
