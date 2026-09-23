package com.arquetipo.demo.registro.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La autenticación real (Firebase) se prueba en {@code AutenticacionExtractorTest} y
 * {@code FirebaseVerificadorTokenIdentidadTest}; aquí solo se mockea
 * {@link AutenticacionExtractor} para probar el enrutado y la autorización propias del
 * controlador (que el {@code uid} autenticado tenga que coincidir con el {@code uid} pedido).
 */
@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@MockitoBean
	private AutenticacionExtractor autenticacion;

	@Test
	void obtenerUsuario_tokenDelMismoUsuario_devuelve200ConLosDatos() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com"));

		mockMvc.perform(get("/api/v1/usuarios/uid-mateo")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("mateo"))
				.andExpect(jsonPath("$.email").value("mateo@example.com"));
	}

	@Test
	void obtenerUsuario_tokenDeOtroUsuario_devuelve403SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-ana")).thenReturn("uid-ana");

		mockMvc.perform(get("/api/v1/usuarios/uid-mateo")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-ana"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:problem-type:forbidden"));

		verify(registroService, never()).obtenerUsuario(any());
	}

	@Test
	void obtenerUsuario_sinCabeceraAuthorization_devuelve401SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(get("/api/v1/usuarios/uid-mateo"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(registroService, never()).obtenerUsuario(any());
	}

	@Test
	void obtenerUsuario_tokenInvalido_devuelve401() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-invalido"))
				.thenThrow(new UnauthorizedException("Token de identidad invalido o expirado"));

		mockMvc.perform(get("/api/v1/usuarios/uid-mateo")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
				.andExpect(status().isUnauthorized());

		verify(registroService, never()).obtenerUsuario(any());
	}

	@Test
	void obtenerUsuario_sinUsuarioConEseUid_devuelve404() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-valido")).thenReturn("uid-inexistente");
		when(registroService.obtenerUsuario("uid-inexistente"))
				.thenThrow(new ResourceNotFoundException("Usuario no encontrado"));

		mockMvc.perform(get("/api/v1/usuarios/uid-inexistente")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-valido"))
				.andExpect(status().isNotFound());
	}

	@Test
	void existeUsername_usuarioAutenticado_devuelve200SinImportarDeQuienEsElUsername() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.existeUsername("ana")).thenReturn(true);

		mockMvc.perform(get("/api/v1/usuarios/existe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.param("username", "ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.existe").value(true));
	}

	@Test
	void existeUsername_usernameLibre_devuelve200ConFalse() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.existeUsername("libre")).thenReturn(false);

		mockMvc.perform(get("/api/v1/usuarios/existe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.param("username", "libre"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.existe").value(false));
	}

	@Test
	void existeUsername_sinCabeceraAuthorization_devuelve401SinLlamarAlServicio() throws Exception {
		when(autenticacion.uidAutenticado(null))
				.thenThrow(new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>"));

		mockMvc.perform(get("/api/v1/usuarios/existe").param("username", "ana"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("urn:problem-type:unauthorized"));

		verify(registroService, never()).existeUsername(any());
	}

	@Test
	void existeUsername_tokenInvalido_devuelve401() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-invalido"))
				.thenThrow(new UnauthorizedException("Token de identidad invalido o expirado"));

		mockMvc.perform(get("/api/v1/usuarios/existe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")
						.param("username", "ana"))
				.andExpect(status().isUnauthorized());

		verify(registroService, never()).existeUsername(any());
	}

	@Test
	void existeUsername_usernameVacio_devuelve400() throws Exception {
		when(autenticacion.uidAutenticado("Bearer token-de-mateo")).thenReturn("uid-mateo");
		when(registroService.existeUsername(""))
				.thenThrow(new ValidationException("username es obligatorio", List.of()));

		mockMvc.perform(get("/api/v1/usuarios/existe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-de-mateo")
						.param("username", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:problem-type:validation-error"));
	}
}
