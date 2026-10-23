package com.wallet.core.shared.security;

import lombok.experimental.UtilityClass;
import org.springframework.core.env.Environment;

/**
 * The deploy profile ({@code SPRING_PROFILES_ACTIVE=prod}). With it the core never makes up a
 * secret that is missing (the development gateway key, an ephemeral JWT key, an ephemeral data
 * key): the boot fails instead, naming the variable to set. A server that comes up "working" with
 * a made-up key logs everyone out, or loses the encrypted data, on its next restart.
 */
@UtilityClass
public class ProductionProfile {

    public static final String NAME = "prod";

    public static boolean isActive(Environment environment) {
        return environment.matchesProfiles(NAME);
    }
}
