package com.arquetipo.demo.common.exception;

import com.arquetipo.demo.common.Constantes.Mensajes;

/**
 * Se lanza al intentar crear un recurso que viola una restriccion de unicidad de negocio.
 * El manejador global la traduce a HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}

	public DuplicateResourceException(String resourceName, String field, Object value) {
		super(Mensajes.FORMATO_RECURSO_DUPLICADO.formatted(resourceName, field, value));
	}
}
