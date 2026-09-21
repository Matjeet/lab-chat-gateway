package com.arquetipo.demo.common.auth.firebase;

import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa el SDK de administración de Firebase para que el gateway pueda verificar tokens
 * de ID él mismo. Mismo proyecto de Firebase que usa {@code chat-registro} para crear las
 * cuentas — aquí solo se usa para <b>verificar</b>, nunca para crear ni eliminar usuarios.
 *
 * <p>Idéntico patrón que {@code FirebaseAppConfig} en {@code chat-registro} (transporte
 * Apache HttpClient por el mismo motivo: evitar que un antivirus/EDR corrompa la respuesta
 * gzip del transporte por defecto). Se puede desactivar por completo con
 * {@code firebase.enabled=false} (los tests lo hacen).
 */
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseAuthConfig {

	@Bean
	public FirebaseApp firebaseApp(@Value("${firebase.credentials-path:}") String credentialsPath) throws IOException {
		if (!FirebaseApp.getApps().isEmpty()) {
			return FirebaseApp.getInstance();
		}
		GoogleCredentials credenciales = credentialsPath == null || credentialsPath.isBlank()
				// Sin ruta explicita: credenciales por defecto del entorno (ADC), p. ej.
				// GOOGLE_APPLICATION_CREDENTIALS o la identidad del propio host en la nube.
				? GoogleCredentials.getApplicationDefault()
				: GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
		FirebaseOptions opciones = FirebaseOptions.builder()
				.setCredentials(credenciales)
				.setHttpTransport(new ApacheHttpTransport())
				.build();
		return FirebaseApp.initializeApp(opciones);
	}

	@Bean
	public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
		return FirebaseAuth.getInstance(firebaseApp);
	}
}
