package org.example.stock_service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StockSchemaMigrationTest {

    @Test
    void migrationSupportsEveryStockMovement() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V3__align_stock_movement_types.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration)
                    .contains("'REASSORT'", "'RESERVATION'", "'LIBERATION'", "'VENTE'",
                            "'RETOUR'", "'PERTE'", "'AJUSTEMENT'");
        }
    }
}
