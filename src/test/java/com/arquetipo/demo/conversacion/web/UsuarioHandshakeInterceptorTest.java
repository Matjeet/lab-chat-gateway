package com.arquetipo.demo.conversacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

class UsuarioHandshakeInterceptorTest {

	private final UsuarioHandshakeInterceptor interceptor = new UsuarioHandshakeInterceptor();

	private static ServerHttpRequest requestCon(String usuario) {
		ServerHttpRequest request = mock(ServerHttpRequest.class);
		when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/chat/" + usuario));
		return request;
	}

	@Test
	void beforeHandshake_usuarioConFormatoValido_dejaPasarYGuardaElAtributo() {
		Map<String, Object> atributos = new HashMap<>();
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean continuar = interceptor.beforeHandshake(requestCon("mateo"), response, null, atributos);

		assertThat(continuar).isTrue();
		assertThat(atributos).containsEntry(UsuarioHandshakeInterceptor.ATRIBUTO_USUARIO, "mateo");
	}

	@Test
	void beforeHandshake_usuarioDemasiadoCorto_rechazaCon400() {
		Map<String, Object> atributos = new HashMap<>();
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean continuar = interceptor.beforeHandshake(requestCon("ma"), response, null, atributos);

		assertThat(continuar).isFalse();
		assertThat(atributos).isEmpty();
		verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
	}

	@Test
	void beforeHandshake_usuarioConCaracteresInvalidos_rechazaCon400() {
		Map<String, Object> atributos = new HashMap<>();
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean continuar = interceptor.beforeHandshake(requestCon("mateo!"), response, null, atributos);

		assertThat(continuar).isFalse();
		verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
	}

	@Test
	void afterHandshake_noHaceNada() {
		// Cubre la rama vacia por completitud de la interfaz; no hay comportamiento que verificar.
		interceptor.afterHandshake(mock(ServerHttpRequest.class), mock(ServerHttpResponse.class), null, null);
	}
}
