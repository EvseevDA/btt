package com.github.evseevda.domainlogicmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Точка входа в модуль бизнес-логики BTT. */
@SpringBootApplication
public class BttDomainLogicModuleApplication {

    /** Запускает Spring Boot с переданными аргументами командной строки. */
    public static void main(String[] args) {
        SpringApplication.run(BttDomainLogicModuleApplication.class, args);
    }
}
