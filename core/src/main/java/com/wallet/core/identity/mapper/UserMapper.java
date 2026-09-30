package com.wallet.core.identity.mapper;

import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.entity.User;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class UserMapper {

    public User toEntity(String normalizedEmail, String passwordHash, String displayName, Instant now) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .displayName(displayName.strip())
                .failedLoginCount(0)
                .createdAt(now)
                .build();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
