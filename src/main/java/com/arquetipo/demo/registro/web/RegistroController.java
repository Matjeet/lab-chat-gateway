package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico de registro de usuarios.
 */
@RestController
@RequestMapping("/api/v1/registro")
@Tag(name = "Registro", description = "Alta de usuarios del servicio")
public class RegistroController {

	private final RegistroService service;

	public RegistroController(RegistroService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegistroResponse registrar(@Valid @RequestBody RegistroRequest request) {
		return service.registrar(request);
	}
}
