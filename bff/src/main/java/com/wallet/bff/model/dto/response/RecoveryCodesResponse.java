package com.wallet.bff.model.dto.response;

import java.util.List;

/** Shown once: the core keeps only their hashes. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
