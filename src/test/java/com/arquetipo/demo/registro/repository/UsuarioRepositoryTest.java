package com.arquetipo.demo.registro.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UsuarioRepositoryTest {

	@Autowired
	private UsuarioRepository repository;

	private static Usuario nuevo() {
		Usuario u = new Usuario();
		u.setUsername("mateo");
		u.setEmail("mateo@example.com");
		u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
		return u;
	}

	@Test
	void guardaYRellenaAuditoria() {
		Usuario guardado = repository.saveAndFlush(nuevo());

		assertThat(guardado.getId()).isNotNull();
		assertThat(guardado.getCreatedAt()).isNotNull();
		assertThat(guardado.getUpdatedAt()).isNotNull();
	}

	@Test
	void existsPorUsernameYEmail_ignoraMayusculas() {
		repository.saveAndFlush(nuevo());

		assertThat(repository.existsByUsernameIgnoreCase("MATEO")).isTrue();
		assertThat(repository.existsByEmailIgnoreCase("Mateo@Example.com")).isTrue();
		assertThat(repository.existsByUsernameIgnoreCase("otro")).isFalse();
	}
}
