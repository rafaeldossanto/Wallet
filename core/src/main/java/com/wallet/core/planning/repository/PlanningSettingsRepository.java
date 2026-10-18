package com.wallet.core.planning.repository;

import com.wallet.core.planning.entity.PlanningSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanningSettingsRepository extends JpaRepository<PlanningSettings, UUID> {
}
