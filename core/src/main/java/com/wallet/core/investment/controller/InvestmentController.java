package com.wallet.core.investment.controller;

import com.wallet.core.investment.dto.PortfolioResponse;
import com.wallet.core.investment.service.InvestmentReadService;
import com.wallet.core.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentReadService investmentReadService;

    @GetMapping("/internal/investments")
    public PortfolioResponse portfolio(@CurrentUserId UUID userId) {
        return investmentReadService.portfolio(userId);
    }
}
