package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.DismissedSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DismissedSuggestionRepository extends JpaRepository<DismissedSuggestion, UUID> {

    List<DismissedSuggestion> findByUserId(UUID userId);
}
