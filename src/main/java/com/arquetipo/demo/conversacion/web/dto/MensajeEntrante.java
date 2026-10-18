package com.arquetipo.demo.conversacion.web.dto;

import com.arquetipo.demo.common.Constantes.Validacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload que envia el cliente por el WebSocket (o el REST, no aplica aqui) para mandar un
 * mensaje de texto. El remitente no viaja en el mensaje: lo determina la sesion (ver
 * {@code ChatWebSocketHandler}). Mismas reglas que exige {@code chat-conversacion}
 * (ver su {@code docs/contratos-api.md} §2.2): el gateway las aplica primero para descartar
 * un mensaje invalido sin siquiera abrir el envio por gRPC.
 */
public record MensajeEntrante(

		/** Mismo formato que {@code RegistroRequest.username} en chat-registro. */
		@NotBlank
		@Size(min = Validacion.USERNAME_LONGITUD_MINIMA, max = Validacion.USERNAME_LONGITUD_MAXIMA)
		@Pattern(regexp = Validacion.USERNAME_PATRON)
		String destinatario,

		@NotBlank
		@Size(max = Validacion.MENSAJE_CONTENIDO_LONGITUD_MAXIMA)
		String contenido
) {
}
