package dev.sablelightoverhaul;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Localizes spec-owned descriptions and legacy display names without changing persisted keys.
 * Keys are stable digests of the exact source text; both locales retain the source inventory.
 * Server messages must keep the Component until delivery so each recipient chooses their language.
 */
public final class LocalizedText {
    private static final ConcurrentHashMap<String, String> KEYS = new ConcurrentHashMap<>();
    private LocalizedText() {}

    public static String key(String source) {
        return KEYS.computeIfAbsent(source, text -> {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
                return "sablelightoverhaul.text." + HexFormat.of().formatHex(hash, 0, 8);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        });
    }

    public static MutableComponent component(String source, Object... arguments) {
        if (source == null || source.isEmpty()) return Component.empty();
        return Component.translatableWithFallback(key(source), source, arguments);
    }

    public static MutableComponent component(String source) {
        if (source == null || source.isEmpty()) return Component.empty();
        return Component.translatableWithFallback(key(source), source.replace("%", "%%"));
    }

    /** Client-side display only. Never use this to translate server messages or stored values. */
    public static String text(String source) {
        return component(source).getString();
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final Component localized;
        private ValidationException(String source, Object... arguments) {
            super(String.format(java.util.Locale.ROOT, source, arguments));
            localized = component(source, arguments);
        }
    }

    public static ValidationException error(String source, Object... arguments) {
        return new ValidationException(source, arguments);
    }

    public static Component errorMessage(Throwable error) {
        return error instanceof ValidationException localized ? localized.localized : component(error.getMessage());
    }
}
