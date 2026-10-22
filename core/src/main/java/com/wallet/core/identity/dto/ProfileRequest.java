package com.wallet.core.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the user can change about themself without a password: the name the app greets. */
public record ProfileRequest(@NotBlank @Size(max = 80) String displayName) {
}
