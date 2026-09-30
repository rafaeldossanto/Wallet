package com.wallet.core.banking.controller;

import com.wallet.core.banking.dto.BillResponse;
import com.wallet.core.banking.dto.CreditCardResponse;
import com.wallet.core.banking.service.BankingReadService;
import com.wallet.core.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final BankingReadService bankingReadService;

    @GetMapping
    public List<CreditCardResponse> list(@CurrentUserId UUID userId) {
        return bankingReadService.listCreditCards(userId);
    }

    @GetMapping("/{accountId}/bills")
    public List<BillResponse> bills(@CurrentUserId UUID userId, @PathVariable UUID accountId) {
        return bankingReadService.listBills(userId, accountId);
    }
}
