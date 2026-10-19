package com.wallet.core.shared.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextKeysTest {

    @Test
    void foldingDropsAccentsAndCase() {
        assertThat(TextKeys.fold("Condomínio SÃO João")).isEqualTo("condominio sao joao");
        assertThat(TextKeys.fold(null)).isNull();
    }

    @Test
    void aKeyKeepsOnlyTheWords() {
        assertThat(TextKeys.key("PAG*CONDOMÍNIO  10/2026")).isEqualTo("pag condominio");
        assertThat(TextKeys.key(" Pix enviado - Maria (parc. 3/12) ")).isEqualTo("pix enviado maria parc");
        assertThat(TextKeys.key("0012345 / 09")).isEmpty();
        assertThat(TextKeys.key(null)).isEmpty();
    }

    @Test
    void wordsAreContainedOnlyWhole() {
        assertThat(TextKeys.containsWords("aluguel apartamento", "aluguel")).isTrue();
        assertThat(TextKeys.containsWords("enel distribuicao sp", "distribuicao sp")).isTrue();
        assertThat(TextKeys.containsWords("aluguel apartamento", "alu")).isFalse();
        assertThat(TextKeys.containsWords("aluguel", "")).isFalse();
        assertThat(TextKeys.containsWords(null, "aluguel")).isFalse();
    }
}
