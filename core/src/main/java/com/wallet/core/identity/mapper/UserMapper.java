package com.wallet.core.identity.mapper;

import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.entity.User;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class UserMapper {

    /** A new account, waiting for the link sent to its e-mail. */
    public User toUnconfirmed(String normalizedEmail, String passwordHash, String displayName, Instant now) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .displayName(displayName.strip())
                .createdAt(now)
                .build();
    }

    /** @param pendingEmail the address the account is moving to, while its link is open; {@code null} otherwise */
    public UserResponse toResponse(User user, String pendingEmail) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt(),
                pendingEmail, user.isTwoFactorEnabled());
    }
}
