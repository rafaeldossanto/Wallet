package com.wallet.core.identity;

import java.util.Optional;
import java.util.UUID;

/** The identity module's read API for the privacy module. */
public interface UserProfiles {

    Optional<UserProfile> find(UUID userId);
}
