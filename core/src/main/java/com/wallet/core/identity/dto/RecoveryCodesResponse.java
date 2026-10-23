package com.wallet.core.identity.dto;

import java.util.List;

/** Shown once: only their hashes are kept. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
