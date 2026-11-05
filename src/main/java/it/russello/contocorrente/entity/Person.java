package it.russello.contocorrente.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "person")
@Getter
@NoArgsConstructor
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false, length = 30)
    private String lastName;

    @Setter
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Setter
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Setter
    @JsonIgnore
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role = Role.user;


    public Person(String firstName, String lastName,
                  LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
    }

}
