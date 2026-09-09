package com.arquetipo.demo.registro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Expone el {@link PasswordEncoder} usado para hashear contrasenas.
 *
 * <p>Se declara aqui (y no se importa {@code spring-boot-starter-security} entero) porque
 * el servicio solo necesita el modulo de criptografia, no la cadena de filtros de seguridad.
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
