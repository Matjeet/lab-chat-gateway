package com.arquetipo.demo.registro.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.common.exception.DuplicateResourceException;
import java.time.Instant;
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
	void registrar_datosValidos_devuelve201SinHash() throws Exception {
		when(registroService.registrar(any()))
				.thenReturn(new RegistroResponse(1L, "mateo", "mateo@example.com", true, Instant.now()));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"secretpass"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.username").value("mateo"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
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
	void registrar_usuarioDuplicado_devuelve409() throws Exception {
		when(registroService.registrar(any()))
				.thenThrow(new DuplicateResourceException("Usuario", "username", "mateo"));

		mockMvc.perform(post("/api/v1/registro")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"mateo","email":"mateo@example.com","password":"secretpass"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Recurso duplicado"));
	}
}
