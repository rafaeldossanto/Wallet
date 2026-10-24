package com.wallet.core.planning;

import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.planning.entity.FixedExpense;
import com.wallet.core.planning.repository.FixedExpenseOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The name tie-break used to be {@code ORDER BY name} in Postgres. It is Java now (the name is
 * encrypted), and the order the user sees must not change: this asks the database itself, with the
 * collation of the database the tests run on, and holds the Java rule to its answer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FixedExpenseOrderIT {

    private static final List<String> NAMES = List.of(
            "Aluguel", "aluguel", "Água", "agua", "Academia", "academia da rua", "Condomínio", "condominio",
            "Cartão", "carro", "Escola", "escola de inglês", "Éden", "Internet", "IPTU", "Zebra", "zelador",
            "Psicólogo", "Plano de saúde", "plano", "Netflix", "Spotify", "Luz", "Gás", "Telefone 2", "Telefone 10",
            "Telefone 1", "Seguro do carro", "Seguro-vida", "Ônibus", "Oficina", "ótica");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theNamesComeOutInTheOrderPostgresGaveThem() {
        List<String> shuffled = new ArrayList<>(NAMES);
        Collections.shuffle(shuffled, new Random(42));

        List<String> byThePostgres = jdbc.queryForList(
                "select n from (values " + "(?),".repeat(shuffled.size() - 1) + "(?)) as t(n) order by n",
                String.class, shuffled.toArray());
        List<String> byTheRule = FixedExpenseOrder.byDayThenName(sameDay(shuffled)).stream().map(FixedExpense::getName).toList();

        assertThat(byTheRule).isEqualTo(byThePostgres);
    }

    private static List<FixedExpense> sameDay(List<String> names) {
        return names.stream()
                .map(name -> FixedExpense.builder().id(UUID.randomUUID()).dayOfMonth(10).name(name).build())
                .toList();
    }
}
