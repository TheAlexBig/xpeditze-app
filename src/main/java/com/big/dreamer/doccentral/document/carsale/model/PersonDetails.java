package com.big.dreamer.doccentral.document.carsale.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PersonDetails(
        @JsonProperty("nombre") @NotBlank String givenName,
        @JsonProperty("apellido") @NotBlank String lastName,
        @JsonProperty("departamento") @NotBlank String state,
        @JsonProperty("domicilio") @NotBlank String settlement,
        @JsonProperty("documento") @NotBlank String document,
        @JsonProperty("genero") @NotBlank String gender,
        @JsonProperty("edad") @NotBlank String age,
        @JsonProperty("oficio") @NotBlank String job,
        @JsonProperty("sociedad") @Valid CompanyDetails company) {

    public PersonDetails(
            String givenName,
            String lastName,
            String state,
            String settlement,
            String document,
            String gender,
            String age,
            String job) {
        this(givenName, lastName, state, settlement, document, gender, age, job, null);
    }
}
