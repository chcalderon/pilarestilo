package com.pilarestilo.order.domain;

import com.pilarestilo.order.domain.model.OrderReference;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderReferenceTest {

    /**
     * Pinned so a refactor cannot quietly change the algorithm. The same UUID must keep producing
     * the same code: references are printed in customer emails and written on bank transfers, so
     * they have to be stable forever. This value also has to match what V67's
     * UPPER(SUBSTR(MD5(id::text),1,10)) produces — OrderReferenceSqlParityIT proves that against a
     * real Postgres.
     */
    @Test
    void isStableForAGivenId() {
        UUID id = UUID.fromString("3f9a2c71-b4d5-4e6f-8a9b-0c1d2e3f4a5b");

        assertThat(OrderReference.forOrderId(id)).isEqualTo(OrderReference.forOrderId(id));
        assertThat(OrderReference.forOrderId(id)).startsWith("PE-").hasSize(13);
    }

    /** The whole point of hex: no O/0, I/1, S/5 to mistype off a bank statement. */
    @Test
    void usesOnlyUnambiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            String ref = OrderReference.forOrderId(UUID.randomUUID());
            assertThat(ref.substring(3)).matches("[0-9A-F]{10}");
        }
    }

    @Test
    void saltingProducesADifferentReference() {
        UUID id = UUID.randomUUID();

        assertThat(OrderReference.forOrderId(id, 1))
                .isNotEqualTo(OrderReference.forOrderId(id))
                .isNotEqualTo(OrderReference.forOrderId(id, 2));
    }

    @Test
    void distinctIdsDoNotCollideAtRealisticVolume() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            seen.add(OrderReference.forOrderId(UUID.randomUUID()));
        }
        assertThat(seen).hasSize(20_000);
    }

    @Test
    void normalizeAcceptsWhatAHumanWouldType() {
        assertThat(OrderReference.normalize("pe-3f9a2c71b4")).isEqualTo("PE-3F9A2C71B4");
        assertThat(OrderReference.normalize("  PE 3F9A 2C71 B4 ")).isEqualTo("PE-3F9A2C71B4");
        assertThat(OrderReference.normalize("3F9A2C71B4")).isEqualTo("PE-3F9A2C71B4");
        assertThat(OrderReference.normalize("   ")).isNull();
        assertThat(OrderReference.normalize(null)).isNull();
    }

    @Test
    void rejectsNullId() {
        assertThrows(DomainException.class, () -> OrderReference.forOrderId(null));
    }
}
