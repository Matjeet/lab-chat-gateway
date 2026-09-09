package com.arquetipo.demo.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

	@Mock
	private UsuarioRepository repository;

	private RegistroService service;

	@BeforeEach
	void setUp() {
		service = new RegistroService(repository, new UsuarioMapper(), new BCryptPasswordEncoder());
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "Mateo@Example.com", "secretpass");
	}

	@Test
	void registrar_hasheaContrasenaYNormalizaEmail() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
			Usuario u = inv.getArgument(0);
			u.setId(1L);
			return u;
		});

		RegistroResponse response = service.registrar(request());

		assertThat(response.id()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("mateo@example.com");
		assertThat(response.username()).isEqualTo("mateo");
	}

	@Test
	void registrar_guardaHashNoLaContrasenaEnClaro() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(false);
		when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(request());

		org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
		org.mockito.Mockito.verify(repository).saveAndFlush(captor.capture());
		Usuario persistido = captor.getValue();
		assertThat(persistido.getPasswordHash())
				.isNotEqualTo("secretpass")
				.startsWith("$2");
		assertThat(new BCryptPasswordEncoder().matches("secretpass", persistido.getPasswordHash())).isTrue();
	}

	@Test
	void registrar_usernameDuplicado_lanza409() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("username");
	}

	@Test
	void registrar_emailDuplicado_lanza409() {
		when(repository.existsByUsernameIgnoreCase("mateo")).thenReturn(false);
		when(repository.existsByEmailIgnoreCase("mateo@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessageContaining("email");
	}
}
