CREATE TABLE account (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         person_id BIGINT NOT NULL,
                         account_number VARCHAR(30) NOT NULL,
                         opened_at DATETIME NOT NULL,
                         balance DECIMAL(15, 2) NOT NULL,
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_account_number (account_number),
                         CONSTRAINT fk_account_person
                             FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB;