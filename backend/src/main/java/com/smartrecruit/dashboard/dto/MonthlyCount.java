package com.smartrecruit.dashboard.dto;

/** Nombre d'éléments créés pendant un mois (UTC), {@code month} au format {@code AAAA-MM}. */
public record MonthlyCount(String month, long count) {
}
