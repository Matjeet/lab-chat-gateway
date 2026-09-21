package com.arquetipo.demo.common.auth.firebase;

import com.arquetipo.demo.common.auth.VerificadorTokenIdentidad;
import com.arquetipo.demo.common.exception.UnauthorizedException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link VerificadorTokenIdentidad} sobre Firebase Authentication (Admin
 * SDK). Es el único sitio del gateway que conoce las clases de Firebase; el resto del código
 * (incluidos todos los controladores) solo ve la abstracción.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseVerificadorTokenIdentidad implements VerificadorTokenIdentidad {

	private final FirebaseAuth firebaseAuth;

	public FirebaseVerificadorTokenIdentidad(FirebaseAuth firebaseAuth) {
		this.firebaseAuth = firebaseAuth;
	}

	@Override
	public String verificar(String idToken) {
		log.debug(">> verificar()");
		try {
			// checkRevoked=true: ademas de firma/expiracion, comprueba que la sesion no se haya
			// revocado explicitamente (p. ej. un cambio de contrasena o un cierre de sesion
			// forzado) despues de emitido el token.
			FirebaseToken token = firebaseAuth.verifyIdToken(idToken, true);
			log.debug("<< verificar() -> OK, uid='{}'", token.getUid());
			return token.getUid();
		} catch (FirebaseAuthException ex) {
			// Nunca se loguea el token en si (es una credencial): solo el codigo de error que
			// reporta Firebase, suficiente para depurar sin exponer nada sensible.
			log.warn("Token de identidad rechazado por Firebase: {}", ex.getAuthErrorCode());
			throw new UnauthorizedException("Token de identidad invalido o expirado");
		} catch (IllegalArgumentException ex) {
			// verifyIdToken lanza esta si el token viene vacio o no tiene forma de JWT.
			log.warn("Token de identidad vacio o mal formado");
			throw new UnauthorizedException("Token de identidad invalido o expirado");
		}
	}
}
