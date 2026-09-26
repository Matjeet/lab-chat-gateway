package com.arquetipo.demo.notificacion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.arquetipo.demo.notificacion.service.NotificacionService;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacionController.class)
class NotificacionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificacionService service;

	@MockitoBean
	private RegistroService registroService;

	@MockitoBean
	private AutenticacionExtractor autenticacion;

	@Test
	void listaNotificaciones_tokenDelMismoUsuario_devuelve200ConLaPagina() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana")).thenReturn(new UsuarioResponse("ana", "ana@example.com"));
		NotificacionResponse notificacion = new NotificacionResponse(
				1L, "mateo", "solicitud", false, Instant.parse("2026-09-25T20:53:47.441193Z"),
				"{\"aceptada\":false,\"pendiente\":true}");
		when(service.listaNotificaciones("ana", 0, 20, "createdAt,desc"))
				.thenReturn(new PageResponse<>(List.of(notificacion), 0, 20, 1, 1, true, true, false));

		mockMvc.perform(get("/api/v1/notificaciones/ana")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].remitente").value("mateo"))
				.andExpect(jsonPath("$.content[0].tipo").value("solicitud"))
				.andExpect(jsonPath("$.content[0].leida").value(false))
				.andExpect(jsonPath("$.content[0].meta").value("{\"aceptada\":false,\"pendiente\":true}"));
	}

	@Test
	void listaNotificaciones_sinNotificaciones_devuelve200ConContentVacio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana")).thenReturn(new UsuarioResponse("ana", "ana@example.com"));
		when(service.listaNotificaciones("ana", 0, 20, "createdAt,desc"))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, true));

		mockMvc.perform(get("/api/v1/notificaciones/ana")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void listaNotificaciones_tokenDeOtroUsuario_devuelve403SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));

		mockMvc.perform(get("/api/v1/notificaciones/ana")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:problem-type:forbidden"));

		verify(service, never()).listaNotificaciones(any(), anyInt(), anyInt(), any());
	}

	@Test
	void listaNotificaciones_sinCabeceraAuthorization_devuelve401SinLlamarANingunServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(get("/api/v1/notificaciones/ana"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(registroService, never()).obtenerUsuario(any());
		verify(service, never()).listaNotificaciones(any(), anyInt(), anyInt(), any());
	}

	@Test
	void listaNotificaciones_conversacionCaida_devuelve503() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana")).thenReturn(new UsuarioResponse("ana", "ana@example.com"));
		when(service.listaNotificaciones("ana", 0, 20, "createdAt,desc"))
				.thenThrow(new ServiceUnavailableException("chat-notificaciones"));

		mockMvc.perform(get("/api/v1/notificaciones/ana")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}

	@Test
	void actualizarLeida_tokenDelMismoUid_devuelve200() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(service.actualizarLeida(1L, true)).thenReturn(new NotificacionResponse(
				1L, "mateo", "solicitud", true, Instant.parse("2026-09-25T20:53:47.441193Z"),
				"{\"aceptada\":false,\"pendiente\":true}"));

		mockMvc.perform(patch("/api/v1/notificaciones/1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"uid-ana","leida":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.leida").value(true));
	}

	@Test
	void actualizarLeida_tokenDeOtroUid_devuelve403SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");

		mockMvc.perform(patch("/api/v1/notificaciones/1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"uid-ana","leida":true}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:problem-type:forbidden"));

		verify(service, never()).actualizarLeida(anyLong(), anyBoolean());
	}

	@Test
	void actualizarLeida_cuerpoInvalido_devuelve400SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");

		mockMvc.perform(patch("/api/v1/notificaciones/1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"","leida":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());

		verify(service, never()).actualizarLeida(anyLong(), anyBoolean());
	}

	@Test
	void actualizarLeida_sinCabeceraAuthorization_devuelve401SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(patch("/api/v1/notificaciones/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"uid-ana","leida":true}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(service, never()).actualizarLeida(anyLong(), anyBoolean());
	}

	@Test
	void actualizarLeida_idInexistente_devuelve404() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(service.actualizarLeida(999L, true))
				.thenThrow(new ResourceNotFoundException("Notificacion no encontrada"));

		mockMvc.perform(patch("/api/v1/notificaciones/999")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"uid-ana","leida":true}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void actualizarLeida_notificacionesCaido_devuelve503() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(service.actualizarLeida(1L, true))
				.thenThrow(new ServiceUnavailableException("chat-notificaciones"));

		mockMvc.perform(patch("/api/v1/notificaciones/1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"uid":"uid-ana","leida":true}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}
}
