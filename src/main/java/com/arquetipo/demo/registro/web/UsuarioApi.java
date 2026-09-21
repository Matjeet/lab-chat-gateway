package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Contrato OpenAPI de la consulta de datos básicos de un usuario, tal como lo expone el
 * gateway al cliente. Mismo contrato que documenta {@code chat-registro} en su
 * {@code docs/contrato-grpc-registro.md} §6: es el único endpoint del gateway que exige
 * autenticación (el resto de {@code chat-registro} — el alta — no la necesita).
 */
@Tag(name = "Usuarios", description = "Datos básicos de un usuario ya registrado, enrutado a chat-registro")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public interface UsuarioApi {

	@Operation(
			summary = "Obtener los datos básicos de un usuario por su uid de Firebase",
			description = """
					Requiere `Authorization: Bearer <idToken>` con el token de ID de Firebase de
					quien pregunta. El gateway reenvía ese token tal cual a `chat-registro`, que
					es quien lo verifica contra Firebase y comprueba que el uid que decodifica
					coincide con el `uid` pedido — un token válido de otro usuario no autoriza a
					leer estos datos.
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Usuario encontrado",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = UsuarioResponse.class),
							examples = @ExampleObject(value = """
									{
									  "username": "mateo",
									  "email": "mateo@example.com"
									}
									"""))),
			@ApiResponse(
					responseCode = "401",
					description = "Falta la cabecera Authorization, o el idToken es inválido/expirado",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "403",
					description = "El idToken es válido pero pertenece a un uid distinto al pedido",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "404",
					description = "Ningún usuario con ese uid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	UsuarioResponse obtenerUsuario(
			@Parameter(description = "UID de Firebase del usuario a consultar", example = "0lSUQS1RdYauzu3ifx6izoyzkvt2")
			String uid,
			String authorization);
}
