package com.arquetipo.demo.notificacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Una notificacion, tal como la ve su receptor. Mismo contrato que {@code NotificacionItem}
 * en {@code chat-notificaciones}. No expone {@code receptor}: es implicito, es el propio
 * usuario que la pide (ver {@code GET /api/v1/notificaciones/{receptor}}).
 */
@Schema(name = "NotificacionResponse", description = "Notificacion de un usuario")
public record NotificacionResponse(

		@Schema(description = "Identificador generado", example = "1")
		Long id,

		@Schema(description = "Username de quien origino la notificacion. Ausente (null) si el "
				+ "tipo de notificacion no tiene remitente.", example = "mateo", nullable = true)
		String remitente,

		@Schema(description = "Tipo de notificacion", example = "solicitud")
		String tipo,

		@Schema(description = "Si el receptor ya la leyo", example = "false")
		boolean leida,

		@Schema(description = "Instante de creacion (UTC)", example = "2026-09-25T20:53:47.441193Z")
		Instant createdAt,

		@Schema(description = "Informacion adicional propia del tipo, como texto JSON tal cual se "
				+ "persisto en chat-notificaciones (sin interpretar). Ausente (null) si la "
				+ "notificacion no tiene meta.", example = "{\"aceptada\":false,\"pendiente\":true}",
				nullable = true)
		String meta,

		@Schema(description = "Avatar de remitente (enlace http(s) o etiqueta <Blobatar .../>, tal cual "
				+ "lo guardo el registro). Null si no hay remitente, si no eligio avatar, o si la "
				+ "notificacion no es una solicitud nueva.",
				example = "https://cdn.example.com/avatares/mateo.png", nullable = true)
		String avatarRemitente
) {
}
