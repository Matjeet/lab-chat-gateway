package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.service.RegistroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial y lista de chats de una conversacion 1 a 1, enrutados por gRPC a
 * {@code chat-conversacion}. El envio de mensajes nuevos va por {@link ChatWebSocketHandler}
 * (WebSocket, {@code /ws/chat/{usuario}}), no por aqui.
 *
 * <p>CORS para {@code /api/**} lo cubre el {@code CorsConfig} global del gateway (mismo que
 * usa el feature de registro) — no hace falta repetirlo aqui, a diferencia de
 * {@code chat-conversacion}, que lo declara por controlador.
 *
 * <p>A diferencia del REST original de {@code chat-conversacion}, aqui los parametros de
 * paginacion ({@code page}/{@code size}/{@code sort} de {@link #historial}, {@code cursor}/
 * {@code size} de {@link #listaChats}) se pasan tal cual por gRPC (que aplica los mismos
 * defaults y limites, ver {@code contrato-grpc-conversacion.md} §4 y §5): el gateway no
 * depende de Spring Data solo para esto.
 *
 * <p>{@code /{usuario}/chats} (segmento literal) y {@code /{usuarioA}/{usuarioB}} (ambos
 * variables) conviven sin ambiguedad: Spring prioriza el segmento literal al resolver la
 * ruta de una peticion concreta.
 *
 * <p>{@link #listaChats} es el unico endpoint autenticado de este controlador — mismo
 * mecanismo que {@code UsuarioController}: {@link AutenticacionExtractor} verifica el
 * {@code idToken} y devuelve el uid autenticado, sin que {@code chat-conversacion} vea nunca
 * el token. La diferencia es que aqui el recurso lo identifica un {@code username} de
 * {@code chat-registro}, no un uid de Firebase, asi que hace falta un paso extra: resolver el
 * {@code username} del uid autenticado con {@link RegistroService#obtenerUsuario} (la misma
 * fuente de verdad que ya usa {@code UsuarioController}) antes de comparar contra
 * {@code usuario}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversaciones")
public class ConversacionController implements ConversacionApi {

	private final ConversacionService service;
	private final RegistroService registroService;
	private final AutenticacionExtractor autenticacion;

	public ConversacionController(ConversacionService service, RegistroService registroService,
			AutenticacionExtractor autenticacion) {
		this.service = service;
		this.registroService = registroService;
		this.autenticacion = autenticacion;
	}

	@Override
	@GetMapping("/{usuarioA}/{usuarioB}")
	public PageResponse<MensajeResponse> historial(
			@PathVariable String usuarioA,
			@PathVariable String usuarioB,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "enviadoEn,asc") String sort) {
		log.debug(">> historial(usuarioA='{}', usuarioB='{}', page={}, size={})", usuarioA, usuarioB, page, size);
		PageResponse<MensajeResponse> respuesta = service.historial(usuarioA, usuarioB, page, size, sort);
		log.debug("<< historial() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	@Override
	@GetMapping("/{usuario}/chats")
	public CursorPage<ChatResumen> listaChats(
			@PathVariable String usuario,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(defaultValue = "") String cursor,
			@RequestParam(defaultValue = "20") int size) {
		log.debug(">> listaChats(usuario='{}', conCursor={}, size={})", usuario, !cursor.isBlank(), size);
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		String usernameAutenticado = registroService.obtenerUsuario(uidAutenticado).username();
		if (!usernameAutenticado.equals(usuario)) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el solicitado. usuario='{}'", usuario);
			throw new ForbiddenException("El token no autoriza a consultar los chats de este usuario");
		}
		CursorPage<ChatResumen> respuesta = service.listaChats(usuario, cursor, size);
		log.debug("<< listaChats() -> OK, chats={}, hasMore={}", respuesta.content().size(), respuesta.hasMore());
		return respuesta;
	}
}
