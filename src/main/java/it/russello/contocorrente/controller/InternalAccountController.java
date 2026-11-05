package it.russello.contocorrente.controller;

import it.russello.contocorrente.repository.AccountRepository;
import it.russello.contocorrente.security.AccountSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalAccountController {
    private final AccountRepository accountRepository;
    private final AccountSecurity accountSecurity;

    @GetMapping("/internal/accounts/by-person/{id}/exists")
    @PreAuthorize("hasRole('admin')")
    public boolean existsByPerson(@PathVariable("id") Long id) {
        return accountRepository.existsByPerson_Id(id);
    }

    @GetMapping("/internal/accounts/{id}/owned")
    @PreAuthorize("hasAnyRole('admin', 'user')")
    public boolean owned(@PathVariable("id") Long id, Authentication authentication) {
        return accountSecurity.isOwner(id, authentication);
    }
}
