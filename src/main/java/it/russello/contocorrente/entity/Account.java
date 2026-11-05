package it.russello.contocorrente.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)     // Rende l'attributo auto incrementale
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_account_person"))
    private Person person;

    @Setter
    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(name = "opened_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime openedAt;      // senza frazioni di secondo

    @Setter
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;


    public Account(Person person, String accountNumber) {
        this.person = person;
        this.accountNumber = accountNumber;
        this.openedAt = LocalDateTime.now().withNano(0);
        this.balance = new BigDecimal("0.00");
    }

}
