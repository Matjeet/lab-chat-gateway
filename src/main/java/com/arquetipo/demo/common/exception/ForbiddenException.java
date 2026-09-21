package com.arquetipo.demo.common.exception;

/**
 * Quien hace la petición demostró una identidad válida (token de Firebase correcto), pero esa
 * identidad no autoriza el recurso pedido — p. ej. un token válido de un usuario distinto al
 * que se consulta. El manejador global la traduce a HTTP 403.
 *
 * <p>Distinta de {@link UnauthorizedException} (401): ahí el problema es el token en sí; aquí
 * el token es válido pero no da permiso sobre este recurso en concreto.
 */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
