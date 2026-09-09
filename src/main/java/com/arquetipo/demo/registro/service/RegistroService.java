package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.sample.exception.DuplicateResourceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del alta de usuarios: valida unicidad, hashea la contrasena y persiste.
 */
@Slf4j
@Service
@Transactional
public class RegistroService {

	private static final String RECURSO = "Usuario";

	private final UsuarioRepository repository;
	private final UsuarioMapper mapper;
	private final PasswordEncoder passwordEncoder;

	public RegistroService(UsuarioRepository repository, UsuarioMapper mapper, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.mapper = mapper;
		this.passwordEncoder = passwordEncoder;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase();

		if (repository.existsByUsernameIgnoreCase(username)) {
			throw new DuplicateResourceException(RECURSO, "username", username);
		}
		if (repository.existsByEmailIgnoreCase(email)) {
			throw new DuplicateResourceException(RECURSO, "email", email);
		}

		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode(request.password()));
		usuario.setActivo(true);

		try {
			Usuario guardado = repository.saveAndFlush(usuario);
			log.debug("Usuario registrado id={} username={}", guardado.getId(), guardado.getUsername());
			return mapper.toResponse(guardado);
		} catch (DataIntegrityViolationException ex) {
			// Carrera entre la comprobacion previa y el insert: lo traducimos a 409.
			throw new DuplicateResourceException(
					"Ya existe un usuario con ese username o email");
		}
	}
}
