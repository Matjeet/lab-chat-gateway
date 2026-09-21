package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de una conversacion 1 a 1, enrutado por gRPC a {@code chat-conversacion}. El
 * envio de mensajes nuevos va por {@link ChatWebSocketHandler} (WebSocket,
 * {@code /ws/chat/{usuario}}), no por aqui.
 *
 * <p>CORS para {@code /api/**} lo cubre el {@code CorsConfig} global del gateway (mismo que
 * usa el feature de registro) — no hace falta repetirlo aqui, a diferencia de
 * {@code chat-conversacion}, que lo declara por controlador.
 *
 * <p>A diferencia del REST original, aqui {@code page}/{@code size}/{@code sort} se pasan tal
 * cual a {@code chat-conversacion} por gRPC (que aplica los mismos defaults y limites, ver
 * {@code contrato-grpc-conversacion.md} §4): el gateway no depende de Spring Data solo para
 * esto.
 */
@RestController
@RequestMapping("/api/v1/conversaciones")
public class ConversacionController implements ConversacionApi {

	private final ConversacionService service;

	public ConversacionController(ConversacionService service) {
		this.service = service;
	}

	@Override
	@GetMapping("/{usuarioA}/{usuarioB}")
	public PageResponse<MensajeResponse> historial(
			@PathVariable String usuarioA,
			@PathVariable String usuarioB,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "enviadoEn,asc") String sort) {
		return service.historial(usuarioA, usuarioB, page, size, sort);
	}
}
