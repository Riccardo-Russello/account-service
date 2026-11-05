package it.russello.contocorrente;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(title = "API Conto Corrente",
                version = "1.0",
                description = "API REST per la gestione di persone, conti corrente e transazioni"))
public class ContocorrenteApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContocorrenteApplication.class, args);
    }

}
