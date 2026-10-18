package com.arquetipo.demo.common.exception;

import com.arquetipo.demo.common.Constantes.Mensajes;

/**
 * Se lanza cuando no existe el recurso solicitado. El manejador global la traduce a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public ResourceNotFoundException(String resourceName, Object id) {
		super(Mensajes.FORMATO_RECURSO_NO_ENCONTRADO.formatted(resourceName, id));
	}
}
