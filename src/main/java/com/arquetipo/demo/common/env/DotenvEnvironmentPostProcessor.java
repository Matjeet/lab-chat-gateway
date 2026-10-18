package com.arquetipo.demo.common.env;

import com.arquetipo.demo.common.Constantes.Mensajes;
import com.arquetipo.demo.common.Constantes.Propiedades;
import com.arquetipo.demo.common.Constantes.Texto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Carga {@code .env} (si existe en el directorio de trabajo) como fuente de propiedades de
 * Spring, con la prioridad mas baja: cualquier variable de entorno real, propiedad de sistema
 * o argumento de linea de comandos la pisa.
 *
 * <p>A diferencia de cargarlo solo en la tarea {@code bootRun} de Gradle (un {@code doFirst}
 * que fija variables de entorno del proceso hijo), esto corre dentro de la propia aplicacion
 * durante el arranque de Spring Boot: funciona igual con {@code ./gradlew bootRun}, desde el
 * IDE (el "Run" de IntelliJ lanza la clase principal directamente, sin pasar por las tareas
 * de Gradle) o como jar (@code java -jar}).
 *
 * <p>Se usa {@link SystemEnvironmentPropertySource} (no un {@code MapPropertySource} a secas)
 * para que las claves en mayusculas con guion bajo del {@code .env} (p. ej.
 * {@code FIREBASE_CREDENTIALS_PATH}) resuelvan los mismos placeholders relajados
 * ({@code ${firebase.credentials-path}}) que resolveria una variable de entorno real.
 *
 * <p>Formato igual que un {@code .env} de shell: {@code CLAVE=valor} por linea, {@code #} para
 * comentarios, comillas simples o dobles opcionales alrededor del valor.
 *
 * <p>Idéntico a la clase homónima de {@code chat-registro} (mismo arquetipo base).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path dotenv = Path.of(Propiedades.ARCHIVO_DOTENV);
		if (!Files.isRegularFile(dotenv)) {
			return;
		}
		Map<String, Object> propiedades = parse(dotenv);
		if (!propiedades.isEmpty()) {
			environment.getPropertySources()
					.addLast(new SystemEnvironmentPropertySource(Propiedades.FUENTE_DOTENV, propiedades));
		}
	}

	private static Map<String, Object> parse(Path dotenv) {
		Map<String, Object> propiedades = new LinkedHashMap<>();
		try {
			List<String> lineas = Files.readAllLines(dotenv);
			for (String rawLinea : lineas) {
				String linea = rawLinea.trim();
				if (linea.isEmpty() || linea.startsWith(Propiedades.PREFIJO_COMENTARIO_DOTENV)) {
					continue;
				}
				int separador = linea.indexOf(Propiedades.SEPARADOR_CLAVE_VALOR_DOTENV);
				if (separador <= Texto.INDICE_PRIMER_CARACTER) {
					continue;
				}
				String clave = linea.substring(Texto.INDICE_PRIMER_CARACTER, separador).trim();
				String valor = quitarComillas(linea.substring(separador + Texto.LONGITUD_UN_CARACTER).trim());
				propiedades.put(clave, valor);
			}
		} catch (IOException ex) {
			throw new IllegalStateException(Mensajes.ERROR_LECTURA_ARCHIVO + dotenv.toAbsolutePath(), ex);
		}
		return propiedades;
	}

	private static String quitarComillas(String valor) {
		if (valor.length() >= Texto.LONGITUD_MINIMA_ENVUELTO_EN_COMILLAS) {
			char primero = valor.charAt(Texto.INDICE_PRIMER_CARACTER);
			char ultimo = valor.charAt(valor.length() - Texto.LONGITUD_UN_CARACTER);
			if ((primero == Texto.COMILLA_DOBLE && ultimo == Texto.COMILLA_DOBLE)
					|| (primero == Texto.COMILLA_SIMPLE && ultimo == Texto.COMILLA_SIMPLE)) {
				return valor.substring(Texto.LONGITUD_UN_CARACTER, valor.length() - Texto.LONGITUD_UN_CARACTER);
			}
		}
		return valor;
	}
}
