package com.wallet.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build when a module reaches into another module's internals.
 * Runs without a Spring context, so it stays in the fast unit suite.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(CoreApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }
}
