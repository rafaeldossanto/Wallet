package com.wallet.core.shared.text;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/** Text compared the way people read it: accents and case do not matter, and in a key neither do digits. */
@UtilityClass
public class TextKeys {

    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}]+");

    /** Accents removed, lower case: "Condomínio" and "CONDOMINIO" both fold to "condominio". Null stays null. */
    public String fold(String text) {
        if (isNull(text)) {
            return null;
        }
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * The words of a text, folded, with digits and punctuation dropped: "PAG*CONDOMÍNIO 10/2026"
     * is "pag condominio". Banks put dates, document numbers and installments in descriptions;
     * the key is what stays the same from one month to the next. Empty when nothing is left.
     */
    public String key(String text) {
        if (isNull(text)) {
            return "";
        }
        return NOT_LETTERS.matcher(fold(text)).replaceAll(" ").strip();
    }

    /** Whether {@code part} appears in {@code whole} as whole words: "aluguel" is in "aluguel apto", "alu" is not. */
    public boolean containsWords(String whole, String part) {
        if (isNull(whole) || isNull(part) || part.isEmpty()) {
            return false;
        }
        return (" " + whole + " ").contains(" " + part + " ");
    }
}
