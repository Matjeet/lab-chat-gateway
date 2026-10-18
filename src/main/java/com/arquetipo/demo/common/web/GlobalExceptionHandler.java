package com.arquetipo.demo.common.web;

import com.arquetipo.demo.common.Constantes.ProblemDetails;
import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.arquetipo.demo.common.exception.ValidationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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
		return problem(HttpStatus.NOT_FOUND, ProblemDetails.TITULO_RECURSO_NO_ENCONTRADO, ex.getMessage(),
				ProblemDetails.TIPO_RECURSO_NO_ENCONTRADO);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
		return problem(HttpStatus.CONFLICT, ProblemDetails.TITULO_RECURSO_DUPLICADO, ex.getMessage(),
				ProblemDetails.TIPO_RECURSO_DUPLICADO);
	}

	@ExceptionHandler(ValidationException.class)
	public ProblemDetail handleValidation(ValidationException ex) {
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, ProblemDetails.TITULO_DATOS_INVALIDOS,
				ProblemDetails.DETALLE_VALIDACION, ProblemDetails.TIPO_ERROR_VALIDACION);
		List<Map<String, String>> errores = ex.getErrores().stream()
				.map(fe -> Map.of(
						ProblemDetails.CAMPO_ERROR_NOMBRE, fe.field(),
						ProblemDetails.CAMPO_ERROR_MENSAJE, fe.message()))
				.toList();
		body.setProperty(ProblemDetails.PROPIEDAD_ERRORES, errores);
		return body;
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
		return problem(HttpStatus.UNAUTHORIZED, ProblemDetails.TITULO_NO_AUTENTICADO, ex.getMessage(),
				ProblemDetails.TIPO_NO_AUTENTICADO);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ProblemDetail handleForbidden(ForbiddenException ex) {
		return problem(HttpStatus.FORBIDDEN, ProblemDetails.TITULO_ACCESO_DENEGADO, ex.getMessage(),
				ProblemDetails.TIPO_ACCESO_DENEGADO);
	}

	@ExceptionHandler(ServiceUnavailableException.class)
	public ProblemDetail handleServiceUnavailable(ServiceUnavailableException ex) {
		log.warn(ex.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetails.TITULO_SERVICIO_NO_DISPONIBLE,
				ProblemDetails.DETALLE_SERVICIO_NO_DISPONIBLE, ProblemDetails.TIPO_SERVICIO_NO_DISPONIBLE);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Excepcion no controlada", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemDetails.TITULO_ERROR_INTERNO,
				ProblemDetails.DETALLE_ERROR_INTERNO, ProblemDetails.TIPO_ERROR_INTERNO);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, ProblemDetails.TITULO_DATOS_INVALIDOS,
				ProblemDetails.DETALLE_VALIDACION, ProblemDetails.TIPO_ERROR_VALIDACION);

		List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> Map.of(
						ProblemDetails.CAMPO_ERROR_NOMBRE, fe.getField(),
						ProblemDetails.CAMPO_ERROR_MENSAJE, fe.getDefaultMessage() == null
								? ProblemDetails.MENSAJE_VALOR_INVALIDO
								: fe.getDefaultMessage()))
				.toList();
		body.setProperty(ProblemDetails.PROPIEDAD_ERRORES, fieldErrors);

		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	private static ProblemDetail problem(HttpStatusCode status, String title, String detail, String type) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setTitle(title);
		body.setType(URI.create(ProblemDetails.PREFIJO_TIPO + type));
		body.setProperty(ProblemDetails.PROPIEDAD_TIMESTAMP, Instant.now());
		return body;
	}
}
