package com.wallet.core.planning.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** @param amount the net salary the user expects, as text ({@code "3500.00"}) */
public record ExpectedSalaryRequest(@NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount) {
}
