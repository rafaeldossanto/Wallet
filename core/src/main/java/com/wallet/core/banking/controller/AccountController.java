package com.wallet.core.banking.controller;

import com.wallet.core.banking.dto.AccountResponse;
import com.wallet.core.banking.service.BankingReadService;
import com.wallet.core.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final BankingReadService bankingReadService;

    /** Flat list with {@code connectionId}; grouping by institution is the BFF's job (it has the names). */
    @GetMapping("/internal/accounts")
    public List<AccountResponse> list(@CurrentUserId UUID userId) {
        return bankingReadService.listAccounts(userId);
    }
}
