package com.arquetipo.demo.conversacion.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
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

@WebMvcTest(ConversacionController.class)
class ConversacionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ConversacionService service;

	@MockitoBean
	private RegistroService registroService;

	@MockitoBean
	private AutenticacionExtractor autenticacion;

	@Test
	void historial_datosValidos_devuelve200ConLaPagina() throws Exception {
		MensajeResponse mensaje = new MensajeResponse("1", "mateo", "ana", "Hola!",
				Instant.parse("2026-09-18T20:53:47.441193Z"));
		when(service.historial("mateo", "ana", 0, 20, "enviadoEn,asc"))
				.thenReturn(new PageResponse<>(List.of(mensaje), 0, 20, 1, 1, true, true, false));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].remitente").value("mateo"))
				.andExpect(jsonPath("$.content[0].destinatario").value("ana"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.empty").value(false));
	}

	@Test
	void historial_sinMensajes_devuelve200ConContentVacio() throws Exception {
		when(service.historial(eq("mateo"), eq("ana"), eq(0), eq(20), eq("enviadoEn,asc")))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, true));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.empty").value(true));
	}

	@Test
	void historial_conParametrosDePaginacion_losReenviaTalCual() throws Exception {
		when(service.historial("mateo", "ana", 1, 50, "enviadoEn,desc"))
				.thenReturn(new PageResponse<>(List.of(), 1, 50, 0, 0, false, true, true));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/ana")
						.param("page", "1")
						.param("size", "50")
						.param("sort", "enviadoEn,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(50));
	}

	@Test
	void historial_conversacionCaida_devuelve503() throws Exception {
		when(service.historial("mateo", "ana", 0, 20, "enviadoEn,asc"))
				.thenThrow(new ServiceUnavailableException("chat-conversacion"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/ana"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}

	@Test
	void listaChats_tokenDelMismoUsuario_devuelve200ConLaPagina() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		MensajeResponse mensaje = new MensajeResponse("1", "mateo", "ana", "Hola!",
				Instant.parse("2026-09-18T20:53:47.441193Z"));
		when(service.listaChats("mateo", "", 20))
				.thenReturn(new CursorPage<>(List.of(new ChatResumen("ana", mensaje)), "cursor-siguiente", true));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].otroUsuario").value("ana"))
				.andExpect(jsonPath("$.content[0].ultimoMensaje.contenido").value("Hola!"))
				.andExpect(jsonPath("$.nextCursor").value("cursor-siguiente"))
				.andExpect(jsonPath("$.hasMore").value(true));
	}

	@Test
	void listaChats_sinChats_devuelve200ConContentVacioYSinMasPaginas() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.listaChats("mateo", "", 20))
				.thenReturn(new CursorPage<>(List.of(), "", false));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void listaChats_conCursor_loReenviaTalCual() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.listaChats("mateo", "cursor-recibido", 50))
				.thenReturn(new CursorPage<>(List.of(), "", false));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.param("cursor", "cursor-recibido")
						.param("size", "50"))
				.andExpect(status().isOk());
	}

	@Test
	void listaChats_cursorInvalido_devuelve400() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.listaChats("mateo", "cursor-invalido", 20))
				.thenThrow(new ValidationException("El cursor de paginacion no es valido", List.of()));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.param("cursor", "cursor-invalido"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
	}

	@Test
	void listaChats_conversacionCaida_devuelve503() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.listaChats("mateo", "", 20))
				.thenThrow(new ServiceUnavailableException("chat-conversacion"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}

	@Test
	void listaChats_tokenDeOtroUsuario_devuelve403SinLlamarAlServicioDeConversacion() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana"))
				.thenReturn(new UsuarioResponse("ana", "ana@example.com"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:problem-type:forbidden"));

		verify(service, never()).listaChats(any(), any(), anyInt());
	}

	@Test
	void listaChats_sinCabeceraAuthorization_devuelve401SinLlamarANingunServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(registroService, never()).obtenerUsuario(any());
		verify(service, never()).listaChats(any(), any(), anyInt());
	}

	@Test
	void listaChats_tokenInvalido_devuelve401() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-invalido"))
				.thenThrow(new UnauthorizedException("Token de identidad invalido o expirado"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
				.andExpect(status().isUnauthorized());

		verify(service, never()).listaChats(any(), any(), anyInt());
	}

	@Test
	void listaChats_autenticadoSinPerfilEnRegistro_devuelve404() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-valido")).thenReturn("uid-sin-perfil");
		when(registroService.obtenerUsuario("uid-sin-perfil"))
				.thenThrow(new ResourceNotFoundException("Usuario no encontrado"));

		mockMvc.perform(get("/api/v1/conversaciones/mateo/chats")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-valido"))
				.andExpect(status().isNotFound());

		verify(service, never()).listaChats(any(), any(), anyInt());
	}

	@Test
	void crearSolicitud_tokenDelSolicitante_devuelve201() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.crearSolicitud("mateo", "ana")).thenReturn(new SolicitudChatResponse(
				"1", "mateo", "ana", false, Instant.parse("2026-09-23T20:53:47.441193Z"), true));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"ana"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("1"))
				.andExpect(jsonPath("$.solicitante").value("mateo"))
				.andExpect(jsonPath("$.solicitado").value("ana"))
				.andExpect(jsonPath("$.aceptada").value(false))
				.andExpect(jsonPath("$.pendiente").value(true));
	}

	@Test
	void crearSolicitud_cuerpoInvalido_devuelve400ConErroresSinLlamarANingunServicio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"m","solicitado":""}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());

		verify(service, never()).crearSolicitud(any(), any());
	}

	@Test
	void crearSolicitud_tokenDeOtroUsuario_devuelve403SinLlamarAlServicioDeConversacion() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana"))
				.thenReturn(new UsuarioResponse("ana", "ana@example.com"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"ana"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:problem-type:forbidden"));

		verify(service, never()).crearSolicitud(any(), any());
	}

	@Test
	void crearSolicitud_sinCabeceraAuthorization_devuelve401SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"ana"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(service, never()).crearSolicitud(any(), any());
	}

	@Test
	void crearSolicitud_usuarioInexistenteEnRegistro_devuelve404() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.crearSolicitud("mateo", "inexistente"))
				.thenThrow(new ResourceNotFoundException("No existe el usuario solicitado 'inexistente'"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"inexistente"}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void crearSolicitud_solicitudDuplicada_devuelve409() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.crearSolicitud("mateo", "ana"))
				.thenThrow(new DuplicateResourceException("Ya existe una solicitud de chat pendiente entre 'mateo' y 'ana'"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"ana"}
								"""))
				.andExpect(status().isConflict());
	}

	@Test
	void crearSolicitud_conversacionCaida_devuelve503() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));
		when(service.crearSolicitud("mateo", "ana"))
				.thenThrow(new ServiceUnavailableException("chat-conversacion"));

		mockMvc.perform(post("/api/v1/conversaciones/solicitudes")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"solicitante":"mateo","solicitado":"ana"}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}
}
