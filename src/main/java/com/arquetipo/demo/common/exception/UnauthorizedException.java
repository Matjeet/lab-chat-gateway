package com.arquetipo.demo.common.exception;

/**
 * Falta la cabecera {@code Authorization: Bearer <idToken>}, o el token que lleva es inválido,
 * está expirado o revocado. El manejador global la traduce a HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
