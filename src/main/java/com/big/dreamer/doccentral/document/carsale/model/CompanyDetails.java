package com.big.dreamer.doccentral.document.carsale.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CompanyDetails(
        @JsonProperty("nombre") @NotBlank String legalName,
        @JsonProperty("abreviatura") String abbreviatedName,
        @JsonProperty("nacionalidad") @NotBlank String nationality,
        @JsonProperty("departamento") @NotBlank String state,
        @JsonProperty("domicilio") @NotBlank String settlement,
        @JsonProperty("nit") @NotBlank String taxId,
        @JsonProperty("cargo_representante") @NotBlank String representativeCapacity,
        @JsonProperty("personeria") @NotBlank String authorityEvidence) {
}
