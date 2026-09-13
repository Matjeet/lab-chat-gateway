package com.arquetipo.demo.common.exception;

import java.util.List;

/**
 * Se lanza cuando un microservicio interno rechaza la peticion por validacion (gRPC
 * {@code INVALID_ARGUMENT}). El manejador global la traduce a HTTP 400 con {@code errors[]},
 * igual que el {@code MethodArgumentNotValidException} de Bean Validation.
 *
 * <p>En condiciones normales el gateway ya valida el cuerpo antes de reenviarlo (mismas
 * anotaciones que el microservicio), asi que esto es una segunda linea de defensa ante un
 * posible desajuste de reglas entre el gateway y el servicio.
 */
public class ValidationException extends RuntimeException {

	private final List<FieldError> errores;

	public ValidationException(String message, List<FieldError> errores) {
		super(message);
		this.errores = errores;
	}

	public List<FieldError> getErrores() {
		return errores;
	}
}
