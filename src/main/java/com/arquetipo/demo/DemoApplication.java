package com.arquetipo.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio chat-registro.
 *
 * <p>Infraestructura transversal (auditoria JPA, excepciones de dominio y traduccion a
 * Problem Details) en {@code com.arquetipo.demo.common}; el flujo de registro en
 * {@code com.arquetipo.demo.registro}.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "chat-registro",
		version = "v1",
		description = "Microservicio de registro de usuarios del sistema Chat"))
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
