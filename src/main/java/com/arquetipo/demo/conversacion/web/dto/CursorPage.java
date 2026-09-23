package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Envoltorio de paginacion por cursor (a diferencia de {@link PageResponse}, que es por
 * pagina/offset) — mismo contrato que {@code CursorPage<T>} en {@code chat-conversacion}. El
 * gateway lo arma directamente {@code ConversacionGrpcClient} a partir de {@code
 * ListaChatsResponse}, sin depender de Spring Data.
 *
 * @param <T> tipo del elemento ya mapeado a DTO de respuesta
 */
@Schema(name = "CursorPage", description = "Pagina de resultados paginada por cursor, no por pagina/offset")
public record CursorPage<T>(

		@Schema(description = "Elementos de esta pagina")
		List<T> content,

		@Schema(description = "Cursor para pedir la siguiente pagina. Vacio si hasMore es false: "
				+ "no lo mandes de vuelta en ese caso, no hay garantia de que siga siendo valido.",
				example = "bWF0ZW8=")
		String nextCursor,

		@Schema(description = "Si hay mas elementos despues de esta pagina")
		boolean hasMore
) {
}
