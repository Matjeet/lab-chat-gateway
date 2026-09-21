package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico de registro de usuarios. El contrato OpenAPI (resumen, respuestas y
 * ejemplos) vive en {@link RegistroApi}; aqui solo queda el enrutado y la delegacion.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/registro")
public class RegistroController implements RegistroApi {

	private final RegistroService service;

	public RegistroController(RegistroService service) {
		this.service = service;
	}

	@Override
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegistroResponse registrar(@Valid @RequestBody RegistroRequest request) {
		log.debug(">> registrar(username='{}', email='{}')", request.username(), request.email());
		RegistroResponse respuesta = service.registrar(request);
		log.debug("<< registrar() -> OK, id={}", respuesta.id());
		return respuesta;
	}
}
