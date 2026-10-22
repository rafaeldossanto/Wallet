package com.wallet.core.identity.config;

import com.wallet.core.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.isNull;

/**
 * A valid token whose user no longer exists is refused like an expired one: 401
 * {@code auth.unauthenticated} (LGPD finding L1-c).
 *
 * <p>A JWT cannot be revoked, so after an account is deleted its access token would still pass for
 * up to 15 minutes. Without this, reads found nothing, but a write in that window (a fixed
 * expense, a salary) hit the foreign key of the deleted user and came back as a 500. Now the app
 * gets a clean 401, tries to renew, finds the refresh token gone with the account, and shows the
 * sign-in. The cost is one primary-key lookup per request.
 */
@Component
@RequiredArgsConstructor
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = userIdOf(jwt);
        if (!users.existsById(userId)) {
            throw new InvalidBearerTokenException("The user of this token no longer exists");
        }
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }

    private static UUID userIdOf(Jwt jwt) {
        if (isNull(jwt.getSubject())) {
            throw new InvalidBearerTokenException("The token has no subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("The token's subject is not a user id");
        }
    }
}
