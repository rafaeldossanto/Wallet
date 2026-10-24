package com.wallet.core.identity.service;

import com.wallet.core.identity.UserProfile;
import com.wallet.core.identity.UserProfiles;
import com.wallet.core.identity.mapper.UserMapper;
import com.wallet.core.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class UserProfileService implements UserProfiles {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> find(UUID userId) {
        return userRepository.findById(userId).map(UserMapper::toProfile);
    }
}
