package com.arquetipo.demo.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

	@Mock
	private RegistroGrpcClient grpcClient;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(grpcClient);
	}

	@Test
	void registrar_delegaEnElClienteGrpcYDevuelveSuRespuesta() {
		RegistroRequest request = new RegistroRequest("mateo", "mateo@example.com", "Passw0rd!23");
		RegistroResponse respuestaEsperada =
				new RegistroResponse(1L, "mateo", "mateo@example.com", "password", true, Instant.now());
		when(grpcClient.registrar(request)).thenReturn(respuestaEsperada);

		RegistroResponse respuesta = service.registrar(request);

		assertThat(respuesta).isEqualTo(respuestaEsperada);
		verify(grpcClient).registrar(any());
	}
}
