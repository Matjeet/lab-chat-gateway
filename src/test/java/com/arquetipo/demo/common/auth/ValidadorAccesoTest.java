package com.arquetipo.demo.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.response.UsuarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidadorAccesoTest {

	@Mock
	private AutenticacionExtractor autenticacion;

	@Mock
	private RegistroService registroService;

	private ValidadorAcceso validador;

	@BeforeEach
	void setUp() {
		validador = new ValidadorAcceso(autenticacion, registroService);
	}

	@Test
	void autenticar_tokenValido_noLanzaNada() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-ana");

		validador.autenticar("Bearer t");

		verify(autenticacion).uidAutenticado("Bearer t");
	}

	@Test
	void autenticar_tokenInvalido_propagaUnauthorized() {
		when(autenticacion.uidAutenticado("Bearer malo")).thenThrow(new UnauthorizedException("invalido"));

		assertThatThrownBy(() -> validador.autenticar("Bearer malo")).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void uidCoincide_mismoUid_devuelveTrue() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-ana");

		assertThat(validador.uidCoincide("Bearer t", "uid-ana")).isTrue();
	}

	@Test
	void uidCoincide_otroUid_devuelveFalse() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-mateo");

		assertThat(validador.uidCoincide("Bearer t", "uid-ana")).isFalse();
	}

	@Test
	void uidCoincide_tokenInvalido_propagaUnauthorized() {
		when(autenticacion.uidAutenticado(null)).thenThrow(new UnauthorizedException("falta"));

		assertThatThrownBy(() -> validador.uidCoincide(null, "uid-ana")).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void usernameCoincide_mismoUsername_devuelveTrue() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana")).thenReturn(new UsuarioResponse("ana", "ana@example.com", null));

		assertThat(validador.usernameCoincide("Bearer t", "ana")).isTrue();
	}

	@Test
	void usernameCoincide_otroUsername_devuelveFalse() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-mateo");
		when(registroService.obtenerUsuario("uid-mateo"))
				.thenReturn(new UsuarioResponse("mateo", "mateo@example.com", null));

		assertThat(validador.usernameCoincide("Bearer t", "ana")).isFalse();
	}

	@Test
	void usernameCoincide_distingueMayusculas() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-ana");
		when(registroService.obtenerUsuario("uid-ana")).thenReturn(new UsuarioResponse("ana", "ana@example.com", null));

		assertThat(validador.usernameCoincide("Bearer t", "Ana")).isFalse();
	}

	@Test
	void usernameCoincide_tokenInvalido_noConsultaRegistro() {
		when(autenticacion.uidAutenticado("Bearer malo")).thenThrow(new UnauthorizedException("invalido"));

		assertThatThrownBy(() -> validador.usernameCoincide("Bearer malo", "ana"))
				.isInstanceOf(UnauthorizedException.class);
		verify(registroService, never()).obtenerUsuario(any());
	}

	@Test
	void usernameCoincide_sinPerfilEnRegistro_propagaNotFound() {
		when(autenticacion.uidAutenticado("Bearer t")).thenReturn("uid-sin-perfil");
		when(registroService.obtenerUsuario("uid-sin-perfil")).thenThrow(new ResourceNotFoundException("no existe"));

		assertThatThrownBy(() -> validador.usernameCoincide("Bearer t", "ana"))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
