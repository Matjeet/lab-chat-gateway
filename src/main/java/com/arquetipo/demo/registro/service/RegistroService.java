package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.mapper.UsuarioMapper;
import com.arquetipo.demo.registro.repository.UsuarioRepository;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas del alta de usuarios: valida unicidad, hashea la contrasena y persiste.
 *
 * <p>Seguridad: ante un conflicto (username o email ya registrados) el cliente recibe
 * siempre el <b>mismo mensaje generico</b>, sin distinguir que campo colisiono ni
 * devolver el valor enviado. El detalle (que campo y con que valor) queda solo en el log
 * del servidor, para no facilitar la enumeracion de cuentas.
 */
@Slf4j
@Service
@Transactional
public class RegistroService {

	/** Mensaje unico que ve el cliente ante cualquier conflicto de unicidad. */
	private static final String CONFLICTO_GENERICO =
			"No se pudo completar el registro con los datos proporcionados";

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
			log.warn("Registro rechazado: el username ya esta registrado. username='{}'", username);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
		if (repository.existsByEmailIgnoreCase(email)) {
			log.warn("Registro rechazado: el email ya esta registrado. email='{}'", email);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}

		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode(request.password()));
		usuario.setActivo(true);

		try {
			Usuario guardado = repository.saveAndFlush(usuario);
			log.debug("Usuario registrado id={} username='{}'", guardado.getId(), guardado.getUsername());
			return mapper.toResponse(guardado);
		} catch (DataIntegrityViolationException ex) {
			// Carrera entre la comprobacion previa y el insert: el detalle va al log, no al cliente.
			log.warn("Registro rechazado por restriccion de unicidad en el insert. username='{}' email='{}'",
					username, email, ex);
			throw new DuplicateResourceException(CONFLICTO_GENERICO);
		}
	}
}
