package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.FieldError;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unico punto donde el gateway habla con {@code chat-registro}. Traduce el DTO REST al
 * mensaje proto (y viceversa) y convierte cualquier error gRPC en la misma excepcion de
 * dominio que lanzaria un servicio local, para que {@code GlobalExceptionHandler} no tenga
 * que conocer el protocolo de transporte interno.
 *
 * <p>Mapeo de errores, espejo de {@code contrato-grpc-registro.md} §4 de chat-registro:
 * {@code INVALID_ARGUMENT} -> 400, {@code ALREADY_EXISTS} -> 409, {@code NOT_FOUND} -> 404
 * (solo en {@code buscarUsuarioPorUid}), {@code UNAVAILABLE} -> 503, cualquier otro -> 500
 * generico. La autenticacion (401/403) la resuelve el propio gateway antes de llegar aqui —
 * ver {@code com.arquetipo.demo.common.auth} — asi que este cliente nunca manda ni recibe un
 * token: {@code chat-registro} no expone ningun codigo de autenticacion en este rpc.
 */
@Slf4j
@Component
public class RegistroGrpcClient {

	private static final String NOMBRE_SERVICIO = "chat-registro";

	private final RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub;

	public RegistroGrpcClient(RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub) {
		this.stub = stub;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		log.debug(">> registrar(username='{}', email='{}')", request.username(), request.email());
		RegistrarUsuarioRequest peticion = RegistrarUsuarioRequest.newBuilder()
				.setUsername(request.username())
				.setEmail(request.email())
				.setPassword(request.password())
				.build();

		try {
			RegistrarUsuarioResponse respuesta = stub.registrar(peticion);
			RegistroResponse resultado = new RegistroResponse(
					respuesta.getId(),
					respuesta.getUsername(),
					respuesta.getEmail(),
					respuesta.getProveedor(),
					respuesta.getActivo(),
					Instant.parse(respuesta.getCreatedAt()));
			log.debug("<< registrar() -> OK, id={}", resultado.id());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito: la ausencia de "<< registrar()" marca el punto exacto
			// del fallo cuando se lee el log de arriba hacia abajo. El detalle real (incluido el
			// log.error de UNAVAILABLE/desconocido) ya queda en traducir().
			throw traducir(ex);
		}
	}

	/**
	 * Resuelve username/email a partir del uid de Firebase de un usuario ya autenticado y
	 * autorizado por el propio gateway (ver {@code com.arquetipo.demo.common.auth}) — a
	 * {@code chat-registro} solo llega el {@code uid}, nunca un token.
	 */
	public UsuarioResponse buscarUsuarioPorUid(String uid) {
		log.debug(">> buscarUsuarioPorUid(uid='{}')", uid);
		BuscarUsuarioPorUidRequest peticion = BuscarUsuarioPorUidRequest.newBuilder()
				.setUid(uid)
				.build();

		try {
			BuscarUsuarioPorUidResponse respuesta = stub.buscarUsuarioPorUid(peticion);
			UsuarioResponse resultado = new UsuarioResponse(respuesta.getUsername(), respuesta.getEmail());
			log.debug("<< buscarUsuarioPorUid() -> OK, username='{}'", resultado.username());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito, mismo criterio que en registrar().
			throw traducir(ex);
		}
	}

	private RuntimeException traducir(StatusRuntimeException ex) {
		String detalle = ex.getStatus().getDescription();
		return switch (ex.getStatus().getCode()) {
			case ALREADY_EXISTS -> new DuplicateResourceException(detalle);
			case INVALID_ARGUMENT -> new ValidationException(detalle, parseFieldErrors(detalle));
			case NOT_FOUND -> new ResourceNotFoundException(detalle);
			case UNAVAILABLE -> {
				log.error("No se pudo contactar con {} por gRPC", NOMBRE_SERVICIO, ex);
				yield new ServiceUnavailableException(NOMBRE_SERVICIO);
			}
			default -> {
				log.error("Fallo inesperado llamando a {} por gRPC (codigo={})",
						NOMBRE_SERVICIO, ex.getStatus().getCode(), ex);
				yield new RuntimeException(
						"Ocurrio un error inesperado al comunicarse con " + NOMBRE_SERVICIO, ex);
			}
		};
	}

	/**
	 * Reconstruye {@code errors[]} a partir de la descripcion de texto libre que documenta
	 * {@code contrato-grpc-registro.md} §4: {@code "... -> campo: mensaje; campo2: mensaje2"}.
	 * Si no sigue ese formato, cae a un unico error generico en vez de fallar.
	 */
	private static List<FieldError> parseFieldErrors(String descripcion) {
		if (descripcion == null || !descripcion.contains("->")) {
			return List.of(new FieldError("_general", descripcion == null ? "" : descripcion));
		}
		String detalle = descripcion.substring(descripcion.indexOf("->") + 2).trim();
		return Arrays.stream(detalle.split(";"))
				.map(String::trim)
				.filter(par -> !par.isBlank())
				.map(par -> {
					int separador = par.indexOf(':');
					return separador < 0
							? new FieldError("_general", par)
							: new FieldError(par.substring(0, separador).trim(), par.substring(separador + 1).trim());
				})
				.toList();
	}
}
