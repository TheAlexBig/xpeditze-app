package com.big.dreamer.doccentral.document.mutual.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MutualCustomInstallment(
        @JsonProperty("fecha") @NotNull LocalDate dueDate,
        @JsonProperty("capital") @NotNull @DecimalMin(value = "0.01") BigDecimal capital,
        @JsonProperty("interes") @NotNull @DecimalMin(value = "0.00") BigDecimal interest) {
}
