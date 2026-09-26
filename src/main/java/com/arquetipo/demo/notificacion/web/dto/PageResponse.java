package com.arquetipo.demo.notificacion.web.dto;

import java.util.List;

/**
 * Envoltorio de paginacion estable para las respuestas de la API, mismo contrato que
 * {@code PageResponse<T>} en {@code chat-notificaciones} (y que su equivalente en el paquete
 * {@code conversacion} de este mismo gateway — cada feature lleva su propia copia, ver
 * {@code arquitectura-gateway.md}). El gateway no depende de Spring Data: lo arma directamente
 * {@code NotificacionGrpcClient} a partir de los campos ya paginados de
 * {@code ListaNotificacionesResponse}.
 *
 * @param <T> tipo del elemento ya mapeado a DTO de respuesta
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last,
		boolean empty
) {
}
