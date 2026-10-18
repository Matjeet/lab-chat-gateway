package com.arquetipo.demo.common.exception;

import com.arquetipo.demo.common.Constantes.Mensajes;

/**
 * Se lanza cuando el gateway no puede alcanzar el microservicio interno al que debia
 * enrutar la peticion (gRPC {@code UNAVAILABLE}: servicio caido, puerto equivocado, etc.).
 * El manejador global la traduce a HTTP 503.
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String servicio) {
		super(Mensajes.FORMATO_SERVICIO_NO_DISPONIBLE.formatted(servicio));
	}
}
