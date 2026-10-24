package com.wallet.core.privacy.controller;

import com.wallet.core.privacy.dto.DataExportResponse;
import com.wallet.core.privacy.service.DataExportService;
import com.wallet.core.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/me")
@RequiredArgsConstructor
public class PrivacyController {

    private final DataExportService exportService;

    /** All the user's data, readable and portable (LGPD, art. 18, II and V). */
    @GetMapping("/export")
    public DataExportResponse export(@CurrentUserId UUID userId) {
        return exportService.export(userId);
    }
}
