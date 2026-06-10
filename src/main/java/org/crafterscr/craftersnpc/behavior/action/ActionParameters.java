package org.crafterscr.craftersnpc.behavior.action;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ActionParameters {
    private ActionParameters() {
    }

    public static Map<String, String> parse(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : input.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Parámetro inválido '" + entry.trim() + "'; usa clave=valor separado por comas.");
            }
            String key = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = entry.substring(separator + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("Las claves y valores de acción no pueden estar vacíos.");
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    public static double decimal(Map<String, String> parameters, String key, double fallback) {
        try {
            return Double.parseDouble(parameters.getOrDefault(key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static int integer(Map<String, String> parameters, String key, int fallback) {
        try {
            return Integer.parseInt(parameters.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
