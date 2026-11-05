package it.russello.contocorrente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(@NotBlank(message = "Numero di conto obbligatorio")
                                   @Size(max = 30, message = "Numero di conto non superiore a 30 caratteri")
                                   String accountNumber) {
}
