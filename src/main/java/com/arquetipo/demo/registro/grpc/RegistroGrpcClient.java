package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.FieldError;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
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
 * {@code INVALID_ARGUMENT} -> 400, {@code ALREADY_EXISTS} -> 409, {@code UNAVAILABLE} -> 503,
 * cualquier otro -> 500 generico.
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
		RegistrarUsuarioRequest peticion = RegistrarUsuarioRequest.newBuilder()
				.setUsername(request.username())
				.setEmail(request.email())
				.setPassword(request.password())
				.build();

		try {
			RegistrarUsuarioResponse respuesta = stub.registrar(peticion);
			return new RegistroResponse(
					respuesta.getId(),
					respuesta.getUsername(),
					respuesta.getEmail(),
					respuesta.getProveedor(),
					respuesta.getActivo(),
					Instant.parse(respuesta.getCreatedAt()));
		} catch (StatusRuntimeException ex) {
			throw traducir(ex);
		}
	}

	private RuntimeException traducir(StatusRuntimeException ex) {
		String detalle = ex.getStatus().getDescription();
		return switch (ex.getStatus().getCode()) {
			case ALREADY_EXISTS -> new DuplicateResourceException(detalle);
			case INVALID_ARGUMENT -> new ValidationException(detalle, parseFieldErrors(detalle));
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
