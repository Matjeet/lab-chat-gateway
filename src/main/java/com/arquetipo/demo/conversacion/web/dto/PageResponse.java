package com.arquetipo.demo.conversacion.web.dto;

import java.util.List;

/**
 * Envoltorio de paginacion estable para las respuestas de la API, mismo contrato que
 * {@code PageResponse<T>} en {@code chat-conversacion}. A diferencia de aquel, este no se
 * construye desde un {@code org.springframework.data.domain.Page} (el gateway no depende de
 * Spring Data): lo arma directamente {@code ConversacionGrpcClient} a partir de los campos ya
 * paginados de {@code HistorialResponse}.
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
