package it.russello.contocorrente.dto;

import java.time.LocalDate;

public record PersonResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth
) {}