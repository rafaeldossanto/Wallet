package com.wallet.core.identity.service;

import com.wallet.core.identity.dto.UserResponse;
import com.wallet.core.identity.mapper.UserMapper;
import com.wallet.core.identity.repository.UserRepository;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserMapper::toResponse)
                // A valid token for a user that no longer exists: treat as logged out.
                .orElseThrow(() -> new DomainException(ErrorType.UNAUTHORIZED, "auth.unauthenticated",
                        "User of this token no longer exists"));
    }
}
