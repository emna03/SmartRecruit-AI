package com.smartrecruit.common.util;

public final class Strings {

    private Strings() {
    }

    /** Chaîne nettoyée, ou null si absente / vide (les champs optionnels vides sont stockés à NULL). */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
