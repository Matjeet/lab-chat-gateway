package com.arquetipo.demo.conversacion.web;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Extrae el {@code usuario} del path de conexion ({@code /ws/chat/{usuario}}) y lo deja en
 * los atributos de la sesion, para que {@link ChatWebSocketHandler} no tenga que reparsear la
 * URL en cada mensaje. Espejo exacto de {@code UsuarioHandshakeInterceptor} en
 * {@code chat-conversacion} — el gateway valida el mismo formato aqui para rechazar la
 * conexion sin siquiera abrir el stream de gRPC.
 *
 * <p>{@code usuario} es el {@code username} de {@code chat-registro}: mismo formato exigido
 * alli (3-50 caracteres, solo {@code A-Z a-z 0-9 . _ -}). Valida el formato, no la identidad
 * real (pendiente en todo el sistema, ver {@code CLAUDE.md}).
 */
public class UsuarioHandshakeInterceptor implements HandshakeInterceptor {

	static final String ATRIBUTO_USUARIO = "usuario";

	/** Mismo formato que {@code RegistroRequest.username} en chat-registro. */
	private static final Pattern FORMATO_USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		String path = request.getURI().getPath();
		String usuario = path.substring(path.lastIndexOf('/') + 1);
		if (!FORMATO_USERNAME.matcher(usuario).matches()) {
			// Sin esto, Spring responde 200 OK sin upgrade (ni error explicito ni Sec-WebSocket-
			// Accept): un cliente real se queda esperando, no lo trata como un rechazo limpio.
			response.setStatusCode(HttpStatus.BAD_REQUEST);
			return false;
		}
		attributes.put(ATRIBUTO_USUARIO, usuario);
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}
}
