package com.arquetipo.demo.common.auth;

/**
 * Puerto hacia el proveedor de identidad externo que verifica tokens de sesión (hoy Firebase
 * Auth; ver {@code common.auth.firebase}).
 *
 * <p><b>El gateway es el único punto del sistema que depende de esto.</b> Los microservicios
 * internos (empezando por {@code chat-registro}) no validan tokens de identidad ellos mismos:
 * reciben del gateway un {@code uid} ya verificado, nunca el token — así, añadir un
 * microservicio nuevo que necesite autenticación no implica integrarlo con Firebase (ni con
 * ningún otro proveedor), solo consumir el {@link AutenticacionExtractor} del gateway.
 *
 * <p>Cambiar de proveedor de identidad (o añadir uno nuevo) es escribir una implementación
 * nueva de esta interfaz, sin tocar {@link AutenticacionExtractor} ni ningún controlador.
 */
public interface VerificadorTokenIdentidad {

	/**
	 * Verifica un token de ID (JWT) emitido por el proveedor al iniciar sesión y devuelve el
	 * uid al que pertenece si es válido.
	 *
	 * @param idToken token de ID tal cual lo manda el cliente
	 * @return el uid que decodifica el token, ya verificado (firma, expiración, revocación)
	 * @throws com.arquetipo.demo.common.exception.UnauthorizedException si el token es
	 *                                                                    inválido, está
	 *                                                                    expirado, revocado o
	 *                                                                    mal formado
	 */
	String verificar(String idToken);
}
