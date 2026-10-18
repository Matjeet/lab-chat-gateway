package com.arquetipo.demo.notificacion.web;

import com.arquetipo.demo.common.Constantes.Mensajes;
import com.arquetipo.demo.common.Constantes.Paginacion;
import com.arquetipo.demo.common.Constantes.Rutas;
import com.arquetipo.demo.common.auth.ValidadorAcceso;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import com.arquetipo.demo.notificacion.web.dto.request.MarcarLeidaRequest;
import com.arquetipo.demo.notificacion.web.dto.response.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.response.PageResponse;
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
 * con {@link ValidadorAcceso#usernameCoincide} y lo compara contra {@code receptor} (403 si no
 * coincide) — el recurso (la bandeja de un usuario) está identificado por username, no por uid.
 *
 * <p>{@link #actualizarLeida} compara por <b>uid directo</b>: como
 * {@code UsuarioController.obtenerUsuario} ({@link ValidadorAcceso#uidCoincide}), compara el uid autenticado tal cual contra el
 * {@code uid} del cuerpo, sin resolver nada contra {@code chat-registro}. Nótese que
 * {@code chat-notificaciones} no vincula una notificación a un uid ni a un username todavía
 * (`ActualizarLeidaRequest` solo lleva `id`/`leida`, ver su `.proto`) — este chequeo prueba que
 * quien llama es quien dice ser, pero no que sea el receptor real de esa notificación
 * concreta. Documentado como limitación conocida en {@code docs/contratos-api.md}.
 */
@Slf4j
@RestController
@RequestMapping(Rutas.NOTIFICACIONES)
public class NotificacionController implements NotificacionApi {

	private final NotificacionService service;
	private final ValidadorAcceso validador;

	public NotificacionController(NotificacionService service, ValidadorAcceso validador) {
		this.service = service;
		this.validador = validador;
	}

	@Override
	@GetMapping(Rutas.NOTIFICACIONES_DE_RECEPTOR)
	public PageResponse<NotificacionResponse> listaNotificaciones(
			@PathVariable String receptor,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(defaultValue = Paginacion.PAGINA_INICIAL) int page,
			@RequestParam(defaultValue = Paginacion.TAMANO_PAGINA_POR_DEFECTO) int size,
			@RequestParam(defaultValue = Paginacion.ORDEN_NOTIFICACIONES_POR_DEFECTO) String sort) {
		log.debug(">> listaNotificaciones(receptor='{}', page={}, size={})", receptor, page, size);
		if (!validador.usernameCoincide(authorization, receptor)) {
			throw new ForbiddenException(Mensajes.SIN_PERMISO_CONSULTAR_NOTIFICACIONES);
		}
		PageResponse<NotificacionResponse> respuesta = service.listaNotificaciones(receptor, page, size, sort);
		log.debug("<< listaNotificaciones() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	@Override
	@PatchMapping(Rutas.NOTIFICACION_POR_ID)
	public NotificacionResponse actualizarLeida(
			@PathVariable long id,
			@Valid @RequestBody MarcarLeidaRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> actualizarLeida(id={}, leida={})", id, request.leida());
		if (!validador.uidCoincide(authorization, request.uid())) {
			throw new ForbiddenException(Mensajes.SIN_PERMISO_MODIFICAR_NOTIFICACION);
		}
		NotificacionResponse respuesta = service.actualizarLeida(id, request.leida());
		log.debug("<< actualizarLeida() -> OK, id={}, leida={}", respuesta.id(), respuesta.leida());
		return respuesta;
	}
}
