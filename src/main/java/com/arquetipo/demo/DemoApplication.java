package com.arquetipo.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de chat-gateway.
 *
 * <p>Unico servicio que el cliente llama por REST o WebSocket: cada feature ({@code registro},
 * {@code conversacion}) traduce su peticion/sesion a una llamada gRPC (unaria o bidi
 * streaming) al microservicio correspondiente. Infraestructura transversal (excepciones de
 * dominio y su traduccion a Problem Details) en {@code com.arquetipo.demo.common}; el enrutado
 * de registro en {@code com.arquetipo.demo.registro}, el de chat en
 * {@code com.arquetipo.demo.conversacion}.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "chat-gateway",
		version = "v1",
		description = "Gateway del sistema Chat: punto de entrada REST/WebSocket, enruta por gRPC a cada microservicio"))
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
