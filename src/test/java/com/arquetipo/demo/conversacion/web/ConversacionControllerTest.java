package com.arquetipo.demo.conversacion.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversacionController.class)
class ConversacionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ConversacionService service;

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
}
