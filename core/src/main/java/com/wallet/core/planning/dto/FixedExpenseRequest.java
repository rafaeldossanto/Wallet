package com.wallet.core.planning.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates or replaces a fixed expense.
 *
 * @param amount     money as text ({@code "220.16"}), at most two decimal places
 * @param dayOfMonth when it is paid; 29 to 31 fall on the last day of a shorter month
 * @param category   optional, a provider category ("Education", "Rent") so the app labels it
 */
public record FixedExpenseRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @Size(max = 100) String category) {
}
