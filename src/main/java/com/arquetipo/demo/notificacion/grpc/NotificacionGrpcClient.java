package com.arquetipo.demo.notificacion.grpc;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unico punto donde el gateway habla con {@code chat-notificaciones}. Traduce los DTO REST al
 * mensaje proto (y viceversa) y convierte cualquier error gRPC en la misma excepcion de
 * dominio que lanzaria un servicio local — espejo de {@code RegistroGrpcClient}/
 * {@code ConversacionGrpcClient}. {@code ListaNotificaciones} y {@code ActualizarLeida} son
 * unarios: los dos metodos son llamadas bloqueantes normales.
 */
@Slf4j
@Component
public class NotificacionGrpcClient {

	private static final String NOMBRE_SERVICIO = "chat-notificaciones";

	private final NotificacionGrpcServiceGrpc.NotificacionGrpcServiceBlockingStub blockingStub;

	public NotificacionGrpcClient(NotificacionGrpcServiceGrpc.NotificacionGrpcServiceBlockingStub blockingStub) {
		this.blockingStub = blockingStub;
	}

	public PageResponse<NotificacionResponse> listaNotificaciones(String receptor, int page, int size, String sort) {
		log.debug(">> listaNotificaciones(receptor='{}', page={}, size={})", receptor, page, size);
		ListaNotificacionesRequest peticion = ListaNotificacionesRequest.newBuilder()
				.setReceptor(receptor)
				.setPage(page)
				.setSize(size)
				.setSort(sort == null ? "" : sort)
				.build();

		try {
			ListaNotificacionesResponse respuesta = blockingStub.listaNotificaciones(peticion);
			PageResponse<NotificacionResponse> resultado = new PageResponse<>(
					respuesta.getContentList().stream().map(this::aNotificacionResponse).toList(),
					respuesta.getPage(),
					respuesta.getSize(),
					respuesta.getTotalElements(),
					respuesta.getTotalPages(),
					respuesta.getFirst(),
					respuesta.getLast(),
					respuesta.getEmpty());
			log.debug("<< listaNotificaciones() -> OK, totalElements={}", resultado.totalElements());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito: el detalle real ya queda en traducir() (log.error).
			throw traducir(ex);
		}
	}

	public NotificacionResponse actualizarLeida(long id, boolean leida) {
		log.debug(">> actualizarLeida(id={}, leida={})", id, leida);
		ActualizarLeidaRequest peticion = ActualizarLeidaRequest.newBuilder()
				.setId(id)
				.setLeida(leida)
				.build();

		try {
			NotificacionItem respuesta = blockingStub.actualizarLeida(peticion);
			NotificacionResponse resultado = aNotificacionResponse(respuesta);
			log.debug("<< actualizarLeida() -> OK, id={}, leida={}", resultado.id(), resultado.leida());
			return resultado;
		} catch (StatusRuntimeException ex) {
			throw traducir(ex);
		}
	}

	private NotificacionResponse aNotificacionResponse(NotificacionItem item) {
		return new NotificacionResponse(
				item.getId(),
				item.hasRemitente() ? item.getRemitente() : null,
				item.getTipo(),
				item.getLeida(),
				Instant.parse(item.getCreatedAt()),
				item.hasMeta() ? item.getMeta() : null,
				item.hasAvatarRemitente() ? item.getAvatarRemitente() : null);
	}

	private RuntimeException traducir(StatusRuntimeException ex) {
		String detalle = ex.getStatus().getDescription();
		return switch (ex.getStatus().getCode()) {
			case INVALID_ARGUMENT -> new ValidationException(detalle, List.of());
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
}
