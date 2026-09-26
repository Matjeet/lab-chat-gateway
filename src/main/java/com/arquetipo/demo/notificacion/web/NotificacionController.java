package com.arquetipo.demo.notificacion.web;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import com.arquetipo.demo.notificacion.web.dto.MarcarLeidaRequest;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.service.RegistroService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificaciones de un usuario, enrutadas por gRPC a {@code chat-notificaciones}. Los dos
 * endpoints exigen autenticación, pero con distinto mecanismo de comparación — decisión
 * explícita, no un descuido:
 *
 * <p>{@link #listaNotificaciones} compara por <b>username</b>: como
 * {@code ConversacionController.listaChats}, resuelve el {@code username} del uid autenticado
 * con {@link RegistroService#obtenerUsuario} y lo compara contra {@code receptor} (403 si no
 * coincide) — el recurso (la bandeja de un usuario) está identificado por username, no por uid.
 *
 * <p>{@link #actualizarLeida} compara por <b>uid directo</b>: como
 * {@code UsuarioController.obtenerUsuario}, compara el uid autenticado tal cual contra el
 * {@code uid} del cuerpo, sin resolver nada contra {@code chat-registro}. Nótese que
 * {@code chat-notificaciones} no vincula una notificación a un uid ni a un username todavía
 * (`ActualizarLeidaRequest` solo lleva `id`/`leida`, ver su `.proto`) — este chequeo prueba que
 * quien llama es quien dice ser, pero no que sea el receptor real de esa notificación
 * concreta. Documentado como limitación conocida en {@code docs/contratos-api.md}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController implements NotificacionApi {

	private final NotificacionService service;
	private final RegistroService registroService;
	private final AutenticacionExtractor autenticacion;

	public NotificacionController(NotificacionService service, RegistroService registroService,
			AutenticacionExtractor autenticacion) {
		this.service = service;
		this.registroService = registroService;
		this.autenticacion = autenticacion;
	}

	@Override
	@GetMapping("/{receptor}")
	public PageResponse<NotificacionResponse> listaNotificaciones(
			@PathVariable String receptor,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort) {
		log.debug(">> listaNotificaciones(receptor='{}', page={}, size={})", receptor, page, size);
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		String usernameAutenticado = registroService.obtenerUsuario(uidAutenticado).username();
		if (!usernameAutenticado.equals(receptor)) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el receptor pedido. receptor='{}'",
					receptor);
			throw new ForbiddenException("El token no autoriza a consultar las notificaciones de este usuario");
		}
		PageResponse<NotificacionResponse> respuesta = service.listaNotificaciones(receptor, page, size, sort);
		log.debug("<< listaNotificaciones() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	@Override
	@PatchMapping("/{id}")
	public NotificacionResponse actualizarLeida(
			@PathVariable long id,
			@Valid @RequestBody MarcarLeidaRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> actualizarLeida(id={}, leida={})", id, request.leida());
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		if (!uidAutenticado.equals(request.uid())) {
			log.warn("Acceso denegado: el uid autenticado no coincide con el uid pedido. id={}", id);
			throw new ForbiddenException("El token no autoriza a modificar esta notificacion");
		}
		NotificacionResponse respuesta = service.actualizarLeida(id, request.leida());
		log.debug("<< actualizarLeida() -> OK, id={}, leida={}", respuesta.id(), respuesta.leida());
		return respuesta;
	}
}
