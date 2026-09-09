package com.arquetipo.demo.common.exception;

/**
 * Se lanza al intentar crear un recurso que viola una restriccion de unicidad de negocio.
 * El manejador global la traduce a HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}

	public DuplicateResourceException(String resourceName, String field, Object value) {
		super("Ya existe %s con %s = %s".formatted(resourceName, field, value));
	}
}
