package dev.sablelightoverhaul.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The diagnostics that answer "why is this lamp not lighting", in their own file.
 *
 * <p>They used to go to the main log, and that is why they kept failing to be useful. The questions this mod
 * asks of itself -- which lamp holds which slot, what the shader was actually handed, whether a field ever
 * spread -- need a line per frame to be worth anything, and a line per frame in {@code latest.log} is spam:
 * fifteen megabytes of modpack log already, two thousand lines of it this mod's, and the reader cannot tell
 * the one line that matters from the four that repeat. So the rate limit came in, then the debug switch, and
 * both of them broke the probe in the ways that matter: a switch that is off prints nothing, and a rate limit
 * with a {@code Long.MIN_VALUE} sentinel printed nothing either, twice, in silence.
 *
 * <p>A separate file removes the reason for either. This is not rotated and not rate limited: it is written
 * next to the game's own logs as {@code sablelightoverhaul-fields.log}, capped at a size beyond which it
 * starts again, and it is only ever written when a probe line is asked for. Reading it is one file, in order,
 * with nothing else in it.
 *
 * <p>Everything goes through {@link #line(String, Object...)} so the format stays uniform and a quote in a
 * message can never throw in the middle of a frame.
 */
final class FieldLog {
    /** Beside the game's own logs, where a reader looks for this mod's output. */
    private static final String NAME = "logs/sablelightoverhaul-fields.log";
    /** Past this size the file starts again. A frame of many lamps is a few hundred bytes. */
    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static Writer writer;
    private static boolean failed;

    private FieldLog() {}

    /**
     * Append one line: {@code HH:mm:ss.SSS  message}, with {@code {}} placeholders filled in order.
     *
     * <p>Never throws and never logs its own failure into the main log -- a diagnostic that can break the game
     * or spam the log it was meant to relieve is worse than no diagnostic.
     */
    static void line(String message, Object... arguments) {
        if (failed) return;
        try {
            if (writer == null) open();
            writer.write(LocalDateTime.now().format(STAMP));
            writer.write("  ");
            writer.write(fill(message, arguments));
            writer.write(System.lineSeparator());
        } catch (IOException | RuntimeException ex) {
            failed = true;
            close();
        }
    }

    /** {@code {}} substitution, so a caller does not have to build strings per frame to decide to drop them. */
    private static String fill(String message, Object[] arguments) {
        if (arguments == null || arguments.length == 0) return message;
        StringBuilder text = new StringBuilder(message.length() + 32);
        int next = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}' && next < arguments.length) {
                text.append(arguments[next++]);
                i++;
            } else {
                text.append(c);
            }
        }
        // Anything left over is appended rather than dropped: a mismatch should be visible in the file, not
        // silently lose the value that was the reason for the line.
        for (int i = next; i < arguments.length; i++) text.append(' ').append(arguments[i]);
        return text.toString();
    }

    private static void open() throws IOException {
        Path path = Path.of(NAME);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (Files.exists(path) && Files.size(path) > MAX_BYTES) {
            // Truncate rather than rotate: the interesting part of a field problem is always what is happening
            // right now, and a directory of old diagnostics is one more thing to search.
            Files.writeString(path, "", StandardCharsets.UTF_8);
        }
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
            // Nothing useful to do: the file is only diagnostics.
        }
        writer = null;
    }

    /** Write out and close, for a clean shutdown. Safe to call more than once. */
    static void flush() {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException ignored) {
            // As above.
        }
        close();
    }
}
