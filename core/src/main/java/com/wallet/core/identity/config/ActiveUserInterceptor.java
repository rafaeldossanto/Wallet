package com.wallet.core.identity.config;

import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * A valid token whose user no longer exists is refused like an expired one: 401
 * {@code auth.unauthenticated} on every route (LGPD finding L1-c).
 *
 * <p>A JWT cannot be revoked, so after an account is deleted its access token still passes for up
 * to 15 minutes. Without this, reads found nothing, but a write in that window (a fixed expense, a
 * salary) hit the foreign key of the deleted user and came back as a 500. Now the app gets a clean
 * 401, tries to renew, finds the refresh token gone with the account, and shows the sign-in. That
 * includes deleting the account a second time: the app tells that end as the deletion.
 *
 * <p>The same goes for a token issued before the last password change or reset
 * ({@code users.password_changed_at}): the refresh tokens are revoked at once, and this ends the
 * access tokens already out, which would otherwise work for up to 15 more minutes for whoever held
 * them. It is the same lookup, so no extra query.
 *
 * <p>An interceptor and not part of the security chain on purpose: what goes wrong here reaches
 * {@code GlobalExceptionHandler}, so a full connection pool is the same 503 {@code service.busy}
 * as anywhere else instead of an error page. The cost is one primary-key lookup per request.
 */
@Component
@RequiredArgsConstructor
public class ActiveUserInterceptor implements HandlerInterceptor {

    private final UserRepository users;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && !users.existsWithPasswordUnchangedSince(userIdOf(token), endOfSecondIssued(token))) {
            throw new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated",
                    "The user of this token no longer exists, or changed the password since it was issued");
        }
        return true;
    }

    /** A token without {@code iat} predates every password change. */
    private static Instant endOfSecondIssued(JwtAuthenticationToken token) {
        Instant issuedAt = token.getToken().getIssuedAt();
        return (isNull(issuedAt) ? Instant.EPOCH : issuedAt.truncatedTo(ChronoUnit.SECONDS)).plusSeconds(1);
    }

    private static UUID userIdOf(JwtAuthenticationToken token) {
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated", "The token's subject is not a user id");
        }
    }
}
