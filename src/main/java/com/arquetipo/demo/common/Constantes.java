package com.arquetipo.demo.common;

import java.time.Duration;
import java.util.List;

/**
 * Unico lugar donde viven los literales de logica y configuracion del gateway (rutas,
 * defaults, reglas de validacion, mensajes de error, nombres de servicio...), agrupados por
 * tema en clases anidadas. Ver "Estandares de codigo" en {@code docs/arquitectura-gateway.md}.
 *
 * <p>Fuera de alcance, a proposito: los mensajes de log y los textos de documentacion OpenAPI
 * (descripciones y ejemplos de {@code @Operation}/{@code @Schema}/{@code @ExampleObject}), que
 * solo se leen en el sitio donde estan escritos.
 */
public final class Constantes {

	private Constantes() {
	}

	/** Rutas REST y WebSocket que expone el gateway. */
	public static final class Rutas {

		public static final String API_V1 = "/api/v1";
		public static final String PATRON_API = "/api/**";

		public static final String REGISTRO = API_V1 + "/registro";

		public static final String USUARIOS = API_V1 + "/usuarios";
		public static final String USUARIO_POR_UID = "/{uid}";
		public static final String USUARIO_EXISTE = "/existe";

		public static final String CONVERSACIONES = API_V1 + "/conversaciones";
		public static final String HISTORIAL = "/{usuarioA}/{usuarioB}";
		public static final String LISTA_CHATS = "/{usuario}/chats";
		public static final String SOLICITUDES = "/solicitudes";

		public static final String NOTIFICACIONES = API_V1 + "/notificaciones";
		public static final String NOTIFICACIONES_DE_RECEPTOR = "/{receptor}";
		public static final String NOTIFICACION_POR_ID = "/{id}";

		public static final String WEBSOCKET_CHAT = "/ws/chat/{usuario}";

		private Rutas() {
		}
	}

	/** Valores por defecto de los parametros de paginacion (como String: los exige {@code @RequestParam}). */
	public static final class Paginacion {

		public static final String PAGINA_INICIAL = "0";
		public static final String TAMANO_PAGINA_POR_DEFECTO = "20";
		public static final String CURSOR_PRIMERA_PAGINA = "";
		public static final String ORDEN_HISTORIAL_POR_DEFECTO = "enviadoEn,asc";
		public static final String ORDEN_NOTIFICACIONES_POR_DEFECTO = "createdAt,desc";

		private Paginacion() {
		}
	}

	/** Reglas de formato, espejo de las que aplican los microservicios destino. */
	public static final class Validacion {

		public static final int USERNAME_LONGITUD_MINIMA = 3;
		public static final int USERNAME_LONGITUD_MAXIMA = 50;
		public static final String USERNAME_PATRON = "^[a-zA-Z0-9._-]+$";
		public static final String USERNAME_PATRON_CON_LONGITUD =
				"^[a-zA-Z0-9._-]{" + USERNAME_LONGITUD_MINIMA + "," + USERNAME_LONGITUD_MAXIMA + "}$";
		public static final String USERNAME_PATRON_MENSAJE = "solo admite letras, numeros y los signos . _ -";

		public static final int EMAIL_LONGITUD_MAXIMA = 255;

		public static final int PASSWORD_LONGITUD_MINIMA = 8;
		public static final int PASSWORD_LONGITUD_MAXIMA = 20;
		public static final String PASSWORD_PATRON =
				"^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])(?!.*(.)\\1{3,}).+$";
		public static final String PASSWORD_PATRON_MENSAJE = "debe tener mayuscula, minuscula, numero y caracter "
				+ "especial, y ningun caracter repetido 4 o mas veces seguidas";

		public static final int AVATAR_LONGITUD_MAXIMA = 500;
		public static final String AVATAR_PATRON = "^(https?://[^\\s\"'<>]+|<Blobatar(\\s[^<>\\r\\n]*)?/>)$";
		public static final String AVATAR_PATRON_MENSAJE = "debe ser un enlace http(s) o una etiqueta "
				+ "<Blobatar ... /> en una sola linea, sin saltos de linea";

		public static final int MENSAJE_CONTENIDO_LONGITUD_MAXIMA = 2000;

		private Validacion() {
		}
	}

	/** Caracteres y posiciones usados al recortar o partir texto. */
	public static final class Texto {

		public static final int INDICE_PRIMER_CARACTER = 0;
		public static final int INDICE_NO_ENCONTRADO = -1;
		public static final int LONGITUD_UN_CARACTER = 1;
		/** Minimo para que una cadena pueda estar envuelta en un par de comillas (apertura + cierre). */
		public static final int LONGITUD_MINIMA_ENVUELTO_EN_COMILLAS = 2;
		public static final char COMILLA_DOBLE = '"';
		public static final char COMILLA_SIMPLE = '\'';
		public static final char SEPARADOR_RUTA = '/';
		public static final String SEPARADOR_LISTA = ",";

		private Texto() {
		}
	}

	/** Cabecera de autenticacion. */
	public static final class Autenticacion {

		public static final String PREFIJO_BEARER = "Bearer ";

		private Autenticacion() {
		}
	}

	/** Detalles de error que acompañan a las excepciones de dominio (van en {@code detail} de la respuesta). */
	public static final class Mensajes {

		public static final String FALTA_CABECERA_AUTHORIZATION = "Falta la cabecera Authorization: Bearer <idToken>";
		public static final String TOKEN_INVALIDO_O_EXPIRADO = "Token de identidad invalido o expirado";

		public static final String SIN_PERMISO_CONSULTAR_USUARIO = "El token no autoriza a consultar este usuario";
		public static final String SIN_PERMISO_CONSULTAR_CHATS = "El token no autoriza a consultar los chats de este usuario";
		public static final String SIN_PERMISO_CREAR_SOLICITUD =
				"El token no autoriza a crear una solicitud en nombre de este usuario";
		public static final String SIN_PERMISO_RESOLVER_SOLICITUD = "El token no autoriza a aceptar o rechazar esta solicitud";
		public static final String SIN_PERMISO_CONSULTAR_NOTIFICACIONES =
				"El token no autoriza a consultar las notificaciones de este usuario";
		public static final String SIN_PERMISO_MODIFICAR_NOTIFICACION = "El token no autoriza a modificar esta notificacion";

		public static final String FORMATO_RECURSO_DUPLICADO = "Ya existe %s con %s = %s";
		public static final String FORMATO_RECURSO_NO_ENCONTRADO = "%s no encontrado con id %s";
		public static final String FORMATO_SERVICIO_NO_DISPONIBLE = "El servicio '%s' no esta disponible";
		public static final String ERROR_INESPERADO_GRPC = "Ocurrio un error inesperado al comunicarse con ";
		public static final String ERROR_LECTURA_ARCHIVO = "No se pudo leer ";
		/** Mensaje de un error de campo cuando el microservicio no mando descripcion. */
		public static final String SIN_DETALLE = "";

		private Mensajes() {
		}
	}

	/** Partes fijas de las respuestas RFC 9457 que arma {@code GlobalExceptionHandler}. */
	public static final class ProblemDetails {

		public static final String PREFIJO_TIPO = "urn:problem-type:";

		public static final String TIPO_RECURSO_NO_ENCONTRADO = "resource-not-found";
		public static final String TIPO_RECURSO_DUPLICADO = "duplicate-resource";
		public static final String TIPO_ERROR_VALIDACION = "validation-error";
		public static final String TIPO_NO_AUTENTICADO = "unauthorized";
		public static final String TIPO_ACCESO_DENEGADO = "forbidden";
		public static final String TIPO_SERVICIO_NO_DISPONIBLE = "service-unavailable";
		public static final String TIPO_ERROR_INTERNO = "internal-error";

		public static final String TITULO_RECURSO_NO_ENCONTRADO = "Recurso no encontrado";
		public static final String TITULO_RECURSO_DUPLICADO = "Recurso duplicado";
		public static final String TITULO_DATOS_INVALIDOS = "Datos invalidos";
		public static final String TITULO_NO_AUTENTICADO = "No autenticado";
		public static final String TITULO_ACCESO_DENEGADO = "Acceso denegado";
		public static final String TITULO_SERVICIO_NO_DISPONIBLE = "Servicio no disponible";
		public static final String TITULO_ERROR_INTERNO = "Error interno";

		public static final String DETALLE_VALIDACION = "El cuerpo de la peticion no supero la validacion";
		public static final String DETALLE_SERVICIO_NO_DISPONIBLE =
				"El servicio no esta disponible en este momento. Intentelo mas tarde.";
		public static final String DETALLE_ERROR_INTERNO = "Ocurrio un error inesperado. Contacte con soporte.";
		public static final String MENSAJE_VALOR_INVALIDO = "valor invalido";

		public static final String PROPIEDAD_TIMESTAMP = "timestamp";
		public static final String PROPIEDAD_ERRORES = "errors";
		public static final String CAMPO_ERROR_NOMBRE = "field";
		public static final String CAMPO_ERROR_MENSAJE = "message";

		private ProblemDetails() {
		}
	}

	/** Microservicios destino y detalles del protocolo gRPC. */
	public static final class Grpc {

		public static final String NOMBRE_SERVICIO_REGISTRO = "chat-registro";
		public static final String NOMBRE_SERVICIO_CONVERSACION = "chat-conversacion";
		public static final String NOMBRE_SERVICIO_NOTIFICACIONES = "chat-notificaciones";

		/** Metodo de {@code ManagedChannel} que Spring invoca al destruir el bean del canal. */
		public static final String METODO_CIERRE_CANAL = "shutdown";
		public static final String HOST_POR_DEFECTO = "localhost";
		/** Un puerto sin configurar llega como 0 (default de {@code int}). */
		public static final int PUERTO_NO_CONFIGURADO = 0;
		public static final int PUERTO_REGISTRO_POR_DEFECTO = 9090;
		public static final int PUERTO_CONVERSACION_POR_DEFECTO = 9091;
		public static final int PUERTO_NOTIFICACIONES_POR_DEFECTO = 9092;

		/** proto3 no distingue un string ausente de uno vacio: "" es como se manda "sin valor". */
		public static final String VALOR_AUSENTE = "";
		public static final String METADATA_USUARIO = "usuario";

		/** Formato de la descripcion de INVALID_ARGUMENT de chat-registro: {@code "... -> campo: mensaje; campo2: mensaje2"}. */
		public static final String SEPARADOR_DETALLE_ERRORES = "->";
		public static final String SEPARADOR_ERRORES = ";";
		public static final char SEPARADOR_CAMPO_MENSAJE = ':';
		public static final String CAMPO_ERROR_GENERAL = "_general";

		private Grpc() {
		}
	}

	/** Sesiones del WebSocket de chat. */
	public static final class WebSocket {

		public static final String ATRIBUTO_USUARIO = "usuario";
		public static final String ATRIBUTO_ENVIO_GRPC = "streamSalienteGrpc";
		public static final int LIMITE_TIEMPO_ENVIO_MS = 10_000;
		public static final int BYTES_POR_KILOBYTE = 1024;
		public static final int LIMITE_BUFFER_KILOBYTES = 512;
		public static final int LIMITE_BUFFER_BYTES = LIMITE_BUFFER_KILOBYTES * BYTES_POR_KILOBYTE;

		private WebSocket() {
		}
	}

	/** CORS para {@code /api/**}. */
	public static final class Cors {

		public static final List<String> METODOS_PERMITIDOS_POR_DEFECTO =
				List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
		public static final String CUALQUIER_CABECERA = "*";
		public static final List<String> CABECERAS_PERMITIDAS_POR_DEFECTO = List.of(CUALQUIER_CABECERA);
		public static final int HORAS_CACHE_PREFLIGHT = 1;
		public static final Duration CACHE_PREFLIGHT_POR_DEFECTO = Duration.ofHours(HORAS_CACHE_PREFLIGHT);

		private Cors() {
		}
	}

	/** Claves de configuracion de {@code application.yml} y del {@code .env}. */
	public static final class Propiedades {

		public static final String PREFIJO_FIREBASE = "firebase";
		public static final String FIREBASE_HABILITADO = "enabled";
		public static final String VALOR_HABILITADO = "true";
		public static final String RUTA_CREDENCIALES_FIREBASE = "${firebase.credentials-path:}";

		public static final String PREFIJO_CORS = "app.cors";
		public static final String ORIGENES_WEBSOCKET = "${app.websocket.allowed-origins:http://localhost:3000}";

		public static final String PREFIJO_SERVICIO_REGISTRO = "servicios.registro";
		public static final String PREFIJO_SERVICIO_CONVERSACION = "servicios.conversacion";
		public static final String PREFIJO_SERVICIO_NOTIFICACIONES = "servicios.notificaciones";

		public static final String ARCHIVO_DOTENV = ".env";
		public static final String FUENTE_DOTENV = "dotenv";
		public static final String PREFIJO_COMENTARIO_DOTENV = "#";
		public static final char SEPARADOR_CLAVE_VALOR_DOTENV = '=';

		private Propiedades() {
		}
	}
}
