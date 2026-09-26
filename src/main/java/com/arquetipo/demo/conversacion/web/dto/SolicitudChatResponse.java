package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Solicitud de chat ya creada, tal como la devuelve {@code chat-conversacion}. Nace siempre con
 * {@code aceptada: false} y {@code pendiente: true} — aceptar o rechazar una solicitud no esta
 * implementado todavia, asi que {@code pendiente} se queda en {@code true} para siempre por
 * ahora. Mientras haya una solicitud pendiente entre dos usuarios,
 * {@code POST /api/v1/conversaciones/solicitudes} no crea una segunda (409).
 */
@Schema(name = "SolicitudChatResponse", description = "Solicitud de chat creada")
public record SolicitudChatResponse(

		@Schema(description = "Identificador generado", example = "66f1c2a8b4c9a12345678901")
		String id,

		@Schema(description = "Quien inicio la solicitud", example = "mateo")
		String solicitante,

		@Schema(description = "Quien la recibio", example = "ana")
		String solicitado,

		@Schema(description = "Si la solicitud ya fue aceptada (siempre false por ahora)", example = "false")
		boolean aceptada,

		@Schema(description = "Instante de creacion (UTC)", example = "2026-09-23T20:53:47.441193Z")
		Instant creadaEn,

		@Schema(description = "Si sigue pendiente de resolver (siempre true por ahora)", example = "true")
		boolean pendiente
) {
}
