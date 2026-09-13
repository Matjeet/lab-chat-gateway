package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Contrato OpenAPI del recurso de registro tal como lo expone el gateway al cliente. Es el
 * mismo contrato que documenta {@code chat-registro} en su {@code docs/contratos-api.md}: el
 * gateway solo cambia quien atiende la conexion TCP, no la forma del JSON.
 *
 * <p>Lo implementa el controlador: springdoc lee las anotaciones heredadas de esta interfaz.
 */
@Tag(name = "Registro", description = "Alta de usuarios del sistema, enrutada a chat-registro")
public interface RegistroApi {

	@Operation(
			summary = "Registrar un usuario",
			description = """
					Da de alta un usuario nuevo. El gateway valida el formato del cuerpo y lo
					reenvia por gRPC a chat-registro, que es quien crea la cuenta en el
					proveedor de identidad y persiste el perfil. El `username` y el `email`
					deben ser unicos (no se distinguen mayusculas de minusculas); el `email`
					se normaliza a minusculas antes de guardarlo.
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
									  "proveedor": "password",
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
					description = """
							Los datos entran en conflicto con una cuenta existente. Por seguridad la
							respuesta es siempre la misma, sin indicar que campo colisiono.
							""",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:duplicate-resource",
									  "title": "Recurso duplicado",
									  "status": 409,
									  "detail": "No se pudo completar el registro con los datos proporcionados",
									  "instance": "/api/v1/registro"
									}
									"""))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-registro no esta disponible en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class),
							examples = @ExampleObject(value = """
									{
									  "type": "urn:problem-type:service-unavailable",
									  "title": "Servicio no disponible",
									  "status": 503,
									  "detail": "El servicio no esta disponible en este momento. Intentelo mas tarde.",
									  "instance": "/api/v1/registro"
									}
									""")))
	})
	RegistroResponse registrar(RegistroRequest request);
}
