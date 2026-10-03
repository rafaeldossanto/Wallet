package com.wallet.bff.service;

import com.wallet.bff.model.dto.response.ConnectionResponse;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** What changes in a user's connections when a sync lands, as one comparable string. */
@UtilityClass
public class SyncStates {

    public static String of(List<ConnectionResponse> connections) {
        return connections.stream()
                .sorted(Comparator.comparing(ConnectionResponse::id))
                .map(connection -> connection.id() + "@" + connection.lastSyncedAt() + ":" + connection.status())
                .collect(Collectors.joining(","));
    }
}
