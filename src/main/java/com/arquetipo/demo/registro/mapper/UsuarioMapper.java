package com.arquetipo.demo.registro.mapper;

import com.arquetipo.demo.registro.domain.Usuario;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual entre {@link Usuario} y sus DTO. La creacion de la entidad vive en el
 * servicio porque necesita hashear la contrasena antes de asignarla.
 */
@Component
public class UsuarioMapper {

	public RegistroResponse toResponse(Usuario usuario) {
		return new RegistroResponse(
				usuario.getId(),
				usuario.getUsername(),
				usuario.getEmail(),
				usuario.isActivo(),
				usuario.getCreatedAt());
	}
}
