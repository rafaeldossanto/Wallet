package com.wallet.core.planning;

import java.util.UUID;

/** The planning module's read API for the privacy module. */
public interface PlanningExports {

    PlanningExport export(UUID userId);
}
