package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.common.Constantes.Mensajes;
import com.arquetipo.demo.common.Constantes.Paginacion;
import com.arquetipo.demo.common.Constantes.Rutas;
import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.request.ActualizarSolicitudRequest;
import com.arquetipo.demo.conversacion.web.dto.request.SolicitudChatRequest;
import com.arquetipo.demo.conversacion.web.dto.response.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.response.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.response.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.response.PageResponse;
import com.arquetipo.demo.conversacion.web.dto.response.SolicitudChatResponse;
import com.arquetipo.demo.registro.service.RegistroService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * <p>{@link #listaChats}, {@link #crearSolicitud} y {@link #actualizarSolicitud} son los
 * endpoints autenticados de este controlador — mismo mecanismo que {@code UsuarioController}:
 * {@link AutenticacionExtractor} verifica el {@code idToken} y devuelve el uid autenticado, sin
 * que {@code chat-conversacion} vea nunca el token. La diferencia es que aqui el recurso lo
 * identifica un {@code username} de {@code chat-registro}, no un uid de Firebase, asi que hace
 * falta un paso extra: resolver el {@code username} del uid autenticado con
 * {@link RegistroService#obtenerUsuario} (la misma fuente de verdad que ya usa
 * {@code UsuarioController}) antes de comparar contra {@code usuario}/{@code solicitante}/
 * {@code solicitado}. En {@link #actualizarSolicitud} se compara contra {@code solicitado}
 * (quien recibio la solicitud), nunca contra {@code solicitante}: solo el receptor puede
 * aceptarla o rechazarla.
 */
@Slf4j
@RestController
@RequestMapping(Rutas.CONVERSACIONES)
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
	@GetMapping(Rutas.HISTORIAL)
	public PageResponse<MensajeResponse> historial(
			@PathVariable String usuarioA,
			@PathVariable String usuarioB,
			@RequestParam(defaultValue = Paginacion.PAGINA_INICIAL) int page,
			@RequestParam(defaultValue = Paginacion.TAMANO_PAGINA_POR_DEFECTO) int size,
			@RequestParam(defaultValue = Paginacion.ORDEN_HISTORIAL_POR_DEFECTO) String sort) {
		log.debug(">> historial(usuarioA='{}', usuarioB='{}', page={}, size={})", usuarioA, usuarioB, page, size);
		PageResponse<MensajeResponse> respuesta = service.historial(usuarioA, usuarioB, page, size, sort);
		log.debug("<< historial() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	@Override
	@GetMapping(Rutas.LISTA_CHATS)
	public CursorPage<ChatResumen> listaChats(
			@PathVariable String usuario,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(defaultValue = Paginacion.CURSOR_PRIMERA_PAGINA) String cursor,
			@RequestParam(defaultValue = Paginacion.TAMANO_PAGINA_POR_DEFECTO) int size) {
		log.debug(">> listaChats(usuario='{}', conCursor={}, size={})", usuario, !cursor.isBlank(), size);
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		String usernameAutenticado = registroService.obtenerUsuario(uidAutenticado).username();
		if (!usernameAutenticado.equals(usuario)) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el solicitado. usuario='{}'", usuario);
			throw new ForbiddenException(Mensajes.SIN_PERMISO_CONSULTAR_CHATS);
		}
		CursorPage<ChatResumen> respuesta = service.listaChats(usuario, cursor, size);
		log.debug("<< listaChats() -> OK, chats={}, hasMore={}", respuesta.content().size(), respuesta.hasMore());
		return respuesta;
	}

	@Override
	@PostMapping(Rutas.SOLICITUDES)
	@ResponseStatus(HttpStatus.CREATED)
	public SolicitudChatResponse crearSolicitud(
			@Valid @RequestBody SolicitudChatRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> crearSolicitud(solicitante='{}', solicitado='{}')", request.solicitante(), request.solicitado());
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		String usernameAutenticado = registroService.obtenerUsuario(uidAutenticado).username();
		if (!usernameAutenticado.equals(request.solicitante())) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el solicitante. solicitante='{}'",
					request.solicitante());
			throw new ForbiddenException(Mensajes.SIN_PERMISO_CREAR_SOLICITUD);
		}
		SolicitudChatResponse respuesta = service.crearSolicitud(request.solicitante(), request.solicitado());
		log.debug("<< crearSolicitud() -> OK, id={}", respuesta.id());
		return respuesta;
	}

	@Override
	@PatchMapping(Rutas.SOLICITUDES)
	public SolicitudChatResponse actualizarSolicitud(
			@Valid @RequestBody ActualizarSolicitudRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> actualizarSolicitud(solicitante='{}', solicitado='{}', aceptada={})",
				request.solicitante(), request.solicitado(), request.aceptada());
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		String usernameAutenticado = registroService.obtenerUsuario(uidAutenticado).username();
		if (!usernameAutenticado.equals(request.solicitado())) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el solicitado. solicitado='{}'",
					request.solicitado());
			throw new ForbiddenException(Mensajes.SIN_PERMISO_RESOLVER_SOLICITUD);
		}
		SolicitudChatResponse respuesta = service.actualizarSolicitud(
				request.solicitante(), request.solicitado(), request.aceptada());
		log.debug("<< actualizarSolicitud() -> OK, id={}", respuesta.id());
		return respuesta;
	}
}
