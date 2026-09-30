package com.wallet.core.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the id of the logged-in user (the JWT subject) into a controller parameter of type
 * {@link java.util.UUID}. Every module reads the user this way, so none of them depends on identity.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
