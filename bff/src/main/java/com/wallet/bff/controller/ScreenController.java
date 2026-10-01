package com.wallet.bff.controller;

import com.wallet.bff.auth.CurrentUserId;
import com.wallet.bff.model.dto.response.BillResponse;
import com.wallet.bff.model.dto.response.CreditCardResponse;
import com.wallet.bff.model.dto.response.InsightsResponse;
import com.wallet.bff.model.dto.response.PageResponse;
import com.wallet.bff.model.dto.response.PortfolioResponse;
import com.wallet.bff.model.dto.response.TransactionResponse;
import com.wallet.bff.service.ScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Filters stay strings: the core parses and validates them, so the two sides cannot disagree. */
@RestController
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    @GetMapping("/api/transactions")
    public PageResponse<TransactionResponse> transactions(@RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(required = false) String accountId,
                                                          @RequestParam(required = false) String direction,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String page,
                                                          @RequestParam(required = false) String pageSize) {
        Map<String, String> filters = new HashMap<>();
        filters.put("from", from);
        filters.put("to", to);
        filters.put("accountId", accountId);
        filters.put("direction", direction);
        filters.put("q", q);
        filters.put("page", page);
        filters.put("pageSize", pageSize);
        return screenService.transactions(filters);
    }

    @GetMapping("/api/cards")
    public List<CreditCardResponse> cards(@CurrentUserId UUID userId) {
        return screenService.cards(userId);
    }

    @GetMapping("/api/cards/{accountId}/bills")
    public List<BillResponse> bills(@PathVariable UUID accountId) {
        return screenService.bills(accountId);
    }

    @GetMapping("/api/investments")
    public PortfolioResponse investments(@CurrentUserId UUID userId) {
        return screenService.investments(userId);
    }

    @GetMapping("/api/insights")
    public InsightsResponse insights(@CurrentUserId UUID userId, @RequestParam(required = false) String month) {
        return screenService.insights(userId, month);
    }
}
