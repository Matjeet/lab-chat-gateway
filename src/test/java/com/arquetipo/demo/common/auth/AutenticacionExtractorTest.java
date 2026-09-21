package com.arquetipo.demo.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutenticacionExtractorTest {

	@Mock
	private VerificadorTokenIdentidad verificador;

	private AutenticacionExtractor autenticacion() {
		return new AutenticacionExtractor(verificador);
	}

	@Test
	void uidAutenticado_cabeceraValida_delegaEnElVerificadorYDevuelveElUid() {
		when(verificador.verificar("token-de-mateo")).thenReturn("uid-mateo");

		String uid = autenticacion().uidAutenticado("Bearer token-de-mateo");

		assertThat(uid).isEqualTo("uid-mateo");
	}

	@Test
	void uidAutenticado_cabeceraNula_lanzaUnauthorizedSinLlamarAlVerificador() {
		assertThatThrownBy(() -> autenticacion().uidAutenticado(null))
				.isInstanceOf(UnauthorizedException.class);

		verifyNoInteractions(verificador);
	}

	@Test
	void uidAutenticado_sinPrefijoBearer_lanzaUnauthorizedSinLlamarAlVerificador() {
		assertThatThrownBy(() -> autenticacion().uidAutenticado("token-de-mateo"))
				.isInstanceOf(UnauthorizedException.class);

		verifyNoInteractions(verificador);
	}

	@Test
	void uidAutenticado_bearerVacio_lanzaUnauthorizedSinLlamarAlVerificador() {
		assertThatThrownBy(() -> autenticacion().uidAutenticado("Bearer    "))
				.isInstanceOf(UnauthorizedException.class);

		verifyNoInteractions(verificador);
	}

	@Test
	void uidAutenticado_tokenInvalido_propagaLaExcepcionDelVerificador() {
		when(verificador.verificar("token-invalido"))
				.thenThrow(new UnauthorizedException("Token de identidad invalido o expirado"));

		assertThatThrownBy(() -> autenticacion().uidAutenticado("Bearer token-invalido"))
				.isInstanceOf(UnauthorizedException.class);
	}
}
