package com.arquetipo.demo.common.exception;

/**
 * Se lanza cuando el gateway no puede alcanzar el microservicio interno al que debia
 * enrutar la peticion (gRPC {@code UNAVAILABLE}: servicio caido, puerto equivocado, etc.).
 * El manejador global la traduce a HTTP 503.
 */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String servicio) {
		super("El servicio '%s' no esta disponible".formatted(servicio));
	}
}
