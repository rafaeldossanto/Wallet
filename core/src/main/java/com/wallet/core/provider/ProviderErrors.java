package com.wallet.core.provider;

import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.experimental.UtilityClass;

/** Error codes any provider adapter uses, so callers handle Pluggy and a future Belvo alike. */
@UtilityClass
public class ProviderErrors {

    public static final String NOT_CONFIGURED = "provider.not_configured";
    public static final String AUTH_FAILED = "provider.auth_failed";
    public static final String NOT_FOUND = "provider.not_found";
    public static final String UNAVAILABLE = "provider.unavailable";

    public DomainException notConfigured() {
        return new DomainException(ErrorType.UNAVAILABLE, NOT_CONFIGURED,
                "Data provider credentials are not set (PLUGGY_CLIENT_ID / PLUGGY_CLIENT_SECRET)");
    }

    public DomainException authFailed() {
        return new DomainException(ErrorType.UNAVAILABLE, AUTH_FAILED, "Data provider rejected our credentials");
    }

    public DomainException notFound(String what) {
        return new DomainException(ErrorType.NOT_FOUND, NOT_FOUND, "Data provider has no " + what);
    }

    public DomainException unavailable(String detail) {
        return new DomainException(ErrorType.UNAVAILABLE, UNAVAILABLE, "Data provider unavailable: " + detail);
    }
}
