package com.arquetipo.demo.registro.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.FieldError;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistroController.class)
class RegistroControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistroService registroService;

	@Test
	void registrar_datosValidos_devuelve201ConProveedor() throws Exception {
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "mateo", "mateo@example.com", "password", true, Instant.now()));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.username").value("mateo"))
				.andExpect(jsonPath("$.proveedor").value("password"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void registrar_cuerpoInvalido_devuelve400ConErrores() throws Exception {
		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"m","email":"no-es-email","password":"corta"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());
	}

	@Test
	void registrar_usuarioDuplicado_devuelve409ConMensajeGenerico() throws Exception {
		when(registroService.registrar(any()))
				.thenThrow(new DuplicateResourceException(
						"No se pudo completar el registro con los datos proporcionados"));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Recurso duplicado"))
				.andExpect(jsonPath("$.detail").value("No se pudo completar el registro con los datos proporcionados"))
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mateo"))));
	}

	@Test
	void registrar_errorDeValidacionReenviadoPorGrpc_devuelve400ConErrores() throws Exception {
		when(registroService.registrar(any())).thenThrow(new ValidationException(
				"El cuerpo de la peticion no supero la validacion",
				List.of(new FieldError("email", "debe ser una direccion de correo electronico con formato correcto"))));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("email"));
	}

	@Test
	void registrar_serviceRegistroCaido_devuelve503() throws Exception {
		when(registroService.registrar(any())).thenThrow(new ServiceUnavailableException("chat-registro"));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:problem-type:service-unavailable"));
	}
}
