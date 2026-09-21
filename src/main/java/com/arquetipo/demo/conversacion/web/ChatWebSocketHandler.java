package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Punto de entrada del chat por WebSocket en el gateway. Espejo de {@code ChatWebSocketHandler}
 * en {@code chat-conversacion}, pero sin persistencia propia: cada sesion abre, al conectarse,
 * un stream {@code Chat} de gRPC hacia {@code chat-conversacion} (via
 * {@link ConversacionService#abrirChat}) y se limita a traducir en los dos sentidos —
 * frame de texto entrante &rarr; {@code MensajeSaliente} del stream, {@code MensajeEntregado}
 * del stream &rarr; frame de texto saliente. La logica de negocio (persistencia, entrega
 * cruzada entre protocolos) vive entera en {@code chat-conversacion}.
 *
 * <p>Se envuelve la sesion en {@link ConcurrentWebSocketSessionDecorator} porque los mensajes
 * que llegan por el stream de gRPC se escriben desde el hilo del cliente gRPC, no desde el
 * hilo propio de la sesion de WebSocket.
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

	private static final String ATRIBUTO_ENVIO_GRPC = "streamSalienteGrpc";
	private static final int LIMITE_ENVIO_MS = 10_000;
	private static final int LIMITE_BUFFER_BYTES = 512 * 1024;

	private final ConversacionService service;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public ChatWebSocketHandler(ConversacionService service, ObjectMapper objectMapper, Validator validator) {
		this.service = service;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	// Abre el stream de gRPC hacia chat-conversacion identificado con el usuario de la sesion.
	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String usuario = usuarioDe(session);
		WebSocketSession sesionSegura =
				new ConcurrentWebSocketSessionDecorator(session, LIMITE_ENVIO_MS, LIMITE_BUFFER_BYTES);

		StreamObserver<MensajeEntrante> streamSaliente = service.abrirChat(usuario, new StreamObserver<>() {

			@Override
			public void onNext(MensajeResponse mensaje) {
				enviarPorSocket(sesionSegura, mensaje);
			}

			@Override
			public void onError(Throwable t) {
				log.warn("El stream de chat con chat-conversacion fallo. usuario='{}'", usuario, t);
				cerrar(sesionSegura, CloseStatus.SERVER_ERROR);
			}

			@Override
			public void onCompleted() {
				cerrar(sesionSegura, CloseStatus.NORMAL);
			}
		});

		session.getAttributes().put(ATRIBUTO_ENVIO_GRPC, streamSaliente);
		log.info("Sesion de chat abierta (gateway -> chat-conversacion). usuario='{}'", usuario);
	}

	// Cierra el lado de envio del stream de gRPC al desconectarse, para que chat-conversacion
	// pueda dar de baja la suscripcion de este usuario a su propio NotificadorTiempoReal.
	@Override
	@SuppressWarnings("unchecked")
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		String usuario = usuarioDe(session);
		Object streamSaliente = session.getAttributes().get(ATRIBUTO_ENVIO_GRPC);
		if (streamSaliente != null) {
			((StreamObserver<MensajeEntrante>) streamSaliente).onCompleted();
		}
		log.info("Sesion de chat cerrada. usuario='{}' status={}", usuario, status);
	}

	// Parsea, valida y reenvia un mensaje entrante por el stream de gRPC ya abierto.
	@Override
	@SuppressWarnings("unchecked")
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String remitente = usuarioDe(session);

		MensajeEntrante entrante;
		try {
			entrante = objectMapper.readValue(message.getPayload(), MensajeEntrante.class);
		} catch (JacksonException ex) {
			log.warn("Mensaje entrante no es JSON valido, se descarta. remitente='{}'", remitente, ex);
			return;
		}

		Set<ConstraintViolation<MensajeEntrante>> violaciones = validator.validate(entrante);
		if (!violaciones.isEmpty()) {
			log.warn("Mensaje entrante invalido, se descarta. remitente='{}' violaciones={}",
					remitente, violaciones.size());
			return;
		}

		Object streamSaliente = session.getAttributes().get(ATRIBUTO_ENVIO_GRPC);
		if (streamSaliente != null) {
			((StreamObserver<MensajeEntrante>) streamSaliente).onNext(entrante);
		}
	}

	// Traduce un mensaje ya confirmado por chat-conversacion a un frame de texto.
	private void enviarPorSocket(WebSocketSession session, MensajeResponse mensaje) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(mensaje)));
		} catch (Exception ex) {
			log.warn("No se pudo reenviar el mensaje por WebSocket.", ex);
		}
	}

	private void cerrar(WebSocketSession session, CloseStatus status) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.close(status);
		} catch (Exception ex) {
			log.warn("No se pudo cerrar la sesion de WebSocket limpiamente.", ex);
		}
	}

	// Saca el usuario de los atributos de la sesion (UsuarioHandshakeInterceptor lo dejo ahi).
	private String usuarioDe(WebSocketSession session) {
		Object usuario = session.getAttributes().get(UsuarioHandshakeInterceptor.ATRIBUTO_USUARIO);
		return usuario == null ? session.getId() : usuario.toString();
	}
}
