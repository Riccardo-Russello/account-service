package it.russello.contocorrente.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.russello.contocorrente.dto.*;
import it.russello.contocorrente.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Conti corrente", description = "Gestione dei conti corrente")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @Operation(summary = "Crea un conto corrente")
    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Elenca i conti correnti con paginazione")
    @GetMapping
    public ResponseEntity<PageResponse<AccountResponse>> findAll(
            @ParameterObject
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.from(accountService.findAll(pageable)));
    }

    @Operation(summary = "Cerca un conto per ID")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> findById(
            @PathVariable("id") Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.findById(id));
    }

    @Operation(summary = "Visualizza il saldo di  un conto")
    @GetMapping("/{id}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable("id") Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getBalance(id));
    }

    @Operation(summary = "Aggiorna il numero di un conto corrente")
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.update(id, request));
    }

    @Operation(summary = "Elimina un conto corrente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        accountService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
