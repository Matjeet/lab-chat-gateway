package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.registro.web.dto.ExisteUsernameResponse;
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
 * Contrato OpenAPI de las consultas sobre usuarios ya registrados, tal como el gateway las
 * expone al cliente. Mismo contrato que documenta {@code chat-registro} en su
 * {@code docs/contrato-grpc-registro.md} §6: ambos endpoints exigen autenticación (a
 * diferencia del alta, {@code POST /api/v1/registro}, que no la necesita), pero
 * {@link #existeUsername} no exige que el recurso pedido pertenezca a quien pregunta — ver
 * {@link UsuarioController}.
 */
@Tag(name = "Usuarios", description = "Consultas sobre usuarios ya registrados, enrutadas a chat-registro")
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

	@Operation(
			summary = "Comprobar si un username ya esta en uso",
			description = """
					Requiere `Authorization: Bearer <idToken>` de cualquier usuario autenticado —
					a diferencia de `GET /api/v1/usuarios/{uid}`, aquí **no** hace falta que el
					`username` consultado sea el propio: es una consulta de disponibilidad (p. ej.
					para saber si se puede iniciar un chat con ese usuario), no un dato protegido.
					No distingue mayúsculas de minúsculas.
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Resultado de la comprobación",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = ExisteUsernameResponse.class),
							examples = @ExampleObject(value = """
									{
									  "existe": true
									}
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El parámetro username falta o está vacío",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "401",
					description = "Falta la cabecera Authorization, o el idToken es inválido/expirado",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-registro no esta disponible en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	ExisteUsernameResponse existeUsername(
			@Parameter(description = "Username a comprobar", example = "mateo") String username,
			String authorization);
}
