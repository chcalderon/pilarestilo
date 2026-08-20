package com.pilarestilo.shared.infrastructure.config;

import com.pilarestilo.notifications.NotificationsPersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one weakness of naming packages by hand.
 *
 * <p>The main EntityManagerFactory no longer scans com.pilarestilo whole, because notifications
 * need an EntityManagerFactory of their own and a scan root cannot exclude a package inside it.
 * The list that replaced the root is only as good as whoever remembers to add to it, and a module
 * left off it is not mapped at all -- a failure that surfaces as a query blowing up in production
 * rather than as anything at build time.
 *
 * <p>So the list is checked against the directories that exist. Both directions matter: a missing
 * package is the bug this exists for, and a stale one is dead weight that makes the next real
 * omission harder to see.
 */
class EntityScanCoversEveryModuleTest {

    private static final Path MODULE_ROOT = Path.of("src", "main", "java", "com", "pilarestilo");

    @Test
    void entity_scan_names_every_module_package_and_nothing_else() throws IOException {
        Set<String> declared = Arrays.stream(
                        EntityScanConfig.class.getAnnotation(EntityScan.class).basePackages())
                .collect(Collectors.toCollection(java.util.HashSet::new));

        /*
         * The notifications root is mapped by the other factory, so it is absent from the main
         * list on purpose. Checking the two together is what makes the rule "every module is
         * mapped by exactly one factory" rather than "every module is in this list".
         */
        declared.add(NotificationsPersistenceConfig.ROOT_PACKAGE);

        Set<String> onDisk;
        try (Stream<Path> entries = Files.list(MODULE_ROOT)) {
            onDisk = entries.filter(Files::isDirectory)
                    .map(directory -> "com.pilarestilo." + directory.getFileName())
                    .collect(Collectors.toSet());
        }

        assertThat(onDisk)
                .as("no module directories under %s -- this test is looking in the wrong place, "
                        + "not reporting a clean codebase", MODULE_ROOT.toAbsolutePath())
                .isNotEmpty();

        assertThat(declared)
                .as("Every package under com/pilarestilo must be mapped by exactly one factory: "
                        + "named in EntityScanConfig, or the notifications root. A module in "
                        + "neither is silently unmapped; a package named in EntityScanConfig that "
                        + "no longer exists hides the next real omission.")
                .containsExactlyInAnyOrderElementsOf(onDisk);
    }
}
