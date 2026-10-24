package com.wallet.bff.exception;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static java.util.Objects.nonNull;

/**
 * An exception as a log can hold it without personal data: the class of each exception in the
 * chain and its stack, never its message. The messages are where the data is: a parser's quote of
 * the body, a value that failed to convert, what the core answered. The class and the frames say
 * where it came from, which is what finding the bug takes.
 */
@UtilityClass
public class ExceptionLog {

    /** Beyond this the rest of a very deep stack adds nothing to finding the line. */
    private static final int MAX_FRAMES = 40;
    private static final int MAX_CAUSES = 10;

    /** The class and the stack of {@code error}, then the same for each cause ("Caused by:"). */
    public static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int depth = 0; nonNull(current) && seen.add(current) && depth < MAX_CAUSES; depth++) {
            text.append(depth == 0 ? "" : "Caused by: ").append(current.getClass().getName()).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            for (int index = 0; index < Math.min(frames.length, MAX_FRAMES); index++) {
                text.append("\tat ").append(frames[index]).append('\n');
            }
            if (frames.length > MAX_FRAMES) {
                text.append("\t... ").append(frames.length - MAX_FRAMES).append(" more\n");
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
