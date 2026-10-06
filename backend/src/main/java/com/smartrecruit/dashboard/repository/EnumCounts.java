package com.smartrecruit.dashboard.repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Comptages par valeur d'énumération, avec 0 pour les valeurs absentes (ordre de déclaration). */
public final class EnumCounts {

    private EnumCounts() {
    }

    public static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }

    /** {@code rows} : lignes {@code [valeur, nombre]} d'une requête {@code group by}. */
    static <E extends Enum<E>> Map<E, Long> of(Class<E> type, List<Object[]> rows) {
        Map<E, Long> counts = zeros(type);
        for (Object[] row : rows) {
            counts.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    public static long total(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
