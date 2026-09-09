package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico de registro de usuarios.
 *
 * <p>Un cliente sin autenticar da de alta un usuario enviando {@code username}, {@code email}
 * y {@code password}. El servicio valida el formato de los tres campos, comprueba que el
 * {@code username} y el {@code email} no esten ya en uso (sin distinguir mayusculas),
 * hashea la contrasena con BCrypt y persiste el usuario en la base de datos.
 *
 * <p>La contrasena en claro nunca se almacena ni se devuelve en las respuestas.
 */
@RestController
@RequestMapping("/api/v1/registro")
@Tag(name = "Registro", description = "Alta de usuarios del servicio")
public class RegistroController {

	private final RegistroService service;

	public RegistroController(RegistroService service) {
		this.service = service;
	}

	/**
	 * Registra un usuario nuevo.
	 *
	 * @param request datos de alta ya validados por Bean Validation
	 * @return el usuario creado, sin la contrasena ni su hash
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Registrar un usuario",
			description = """
					Da de alta un usuario nuevo. El `username` y el `email` deben ser unicos
					(no se distinguen mayusculas de minusculas) y el `email` se normaliza a
					minusculas antes de guardarlo. La contrasena se almacena solo como hash BCrypt.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "201",
					description = "Usuario registrado",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = RegistroResponse.class),
							examples = @ExampleObject(value = """
									{
									  "id": 1,
									  "username": "mateo",
									  "email": "mateo@example.com",
									  "activo": true,
									  "createdAt": "2026-09-08T20:53:47.441193Z"
									}
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El cuerpo no supero la validacion (formato de los campos)",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:validation-error",
									  "title": "Datos invalidos",
									  "status": 400,
									  "detail": "El cuerpo de la peticion no supero la validacion",
									  "instance": "/api/v1/registro",
									  "errors": [
									    { "field": "email", "message": "debe ser una direccion de correo electronico con formato correcto" }
									  ]
									}
									"""))),
			@ApiResponse(
					responseCode = "409",
					description = "El username o el email ya estan registrados",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:duplicate-resource",
									  "title": "Recurso duplicado",
									  "status": 409,
									  "detail": "Ya existe Usuario con username = mateo",
									  "instance": "/api/v1/registro"
									}
									""")))
	})
	public RegistroResponse registrar(@Valid @RequestBody RegistroRequest request) {
		return service.registrar(request);
	}
}
