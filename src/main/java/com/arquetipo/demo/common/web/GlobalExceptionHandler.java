package com.arquetipo.demo.common.web;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones de la aplicacion a respuestas HTTP con formato
 * <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 (Problem Details)</a>.
 *
 * <p>Es el unico lugar donde se decide el codigo de estado: los servicios lanzan excepciones
 * de dominio y los controladores no llevan bloques try/catch.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), "resource-not-found");
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
		return problem(HttpStatus.CONFLICT, "Recurso duplicado", ex.getMessage(), "duplicate-resource");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Violacion de integridad de datos", ex);
		return problem(HttpStatus.CONFLICT, "Conflicto de datos",
				"La operacion viola una restriccion de integridad", "data-integrity");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Excepcion no controlada", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrio un error inesperado. Contacte con soporte.", "internal-error");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Datos invalidos",
				"El cuerpo de la peticion no supero la validacion", "validation-error");

		List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> Map.of(
						"field", fe.getField(),
						"message", fe.getDefaultMessage() == null ? "valor invalido" : fe.getDefaultMessage()))
				.toList();
		body.setProperty("errors", fieldErrors);

		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	private static ProblemDetail problem(HttpStatusCode status, String title, String detail, String type) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setTitle(title);
		body.setType(URI.create("urn:problem-type:" + type));
		body.setProperty("timestamp", Instant.now());
		return body;
	}
}
