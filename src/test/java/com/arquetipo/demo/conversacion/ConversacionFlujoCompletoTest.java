package com.arquetipo.demo.conversacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquetipo.demo.common.auth.VerificadorTokenIdentidad;
import com.arquetipo.demo.conversacion.grpc.ConversacionGrpcServiceGrpc;
import com.arquetipo.demo.conversacion.grpc.HistorialRequest;
import com.arquetipo.demo.conversacion.grpc.HistorialResponse;
import com.arquetipo.demo.conversacion.grpc.MensajeEntregado;
import com.arquetipo.demo.conversacion.grpc.MensajeSaliente;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Prueba el flujo completo que debe seguir SIEMPRE una peticion de chat que entra al gateway:
 *
 * <ol>
 *   <li>Cliente -> gateway por WebSocket ({@code /ws/chat/{usuario}}) o REST (historial).</li>
 *   <li>El gateway abre/usa una llamada gRPC hacia chat-conversacion.</li>
 *   <li>El servicio (aqui, un doble de chat-conversacion) procesa y responde por gRPC.</li>
 *   <li>El gateway traduce esa respuesta de vuelta al cliente por el mismo protocolo con el
 *       que entro.</li>
 * </ol>
 *
 * <p>Se levanta el contexto de Spring completo contra un servidor gRPC real por TCP, igual
 * patron que {@code RegistroFlujoCompletoTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ConversacionFlujoCompletoTest {

	// Con firebase.enabled=false (perfil de test) no hay ningun bean real de
	// VerificadorTokenIdentidad; AutenticacionExtractor (UsuarioController) necesita uno para
	// poder construirse, aunque este test no ejercite ese endpoint.
	@MockitoBean
	private VerificadorTokenIdentidad verificadorTokenIdentidad;

	private static final Metadata.Key<String> USUARIO_METADATA_KEY =
			Metadata.Key.of("usuario", Metadata.ASCII_STRING_MARSHALLER);
	private static final Context.Key<String> USUARIO_CONTEXT_KEY = Context.key("usuario");

	private static Server servidorFalsoChatConversacion;

	private static final AtomicReference<BiConsumer<MensajeSaliente, StreamObserver<MensajeEntregado>>>
			comportamientoChat = new AtomicReference<>();
	private static final AtomicReference<BiConsumer<HistorialRequest, StreamObserver<HistorialResponse>>>
			comportamientoHistorial = new AtomicReference<>();

	@DynamicPropertySource
	static void apuntarElGatewayAlServidorFalso(DynamicPropertyRegistry registry) throws IOException {
		servidorFalsoChatConversacion = ServerBuilder.forPort(0)
				.addService(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
					@Override
					public StreamObserver<MensajeSaliente> chat(StreamObserver<MensajeEntregado> responseObserver) {
						return new StreamObserver<>() {
							@Override
							public void onNext(MensajeSaliente value) {
								comportamientoChat.get().accept(value, responseObserver);
							}

							@Override
							public void onError(Throwable t) { }

							@Override
							public void onCompleted() {
								responseObserver.onCompleted();
							}
						};
					}

					@Override
					public void historial(HistorialRequest request, StreamObserver<HistorialResponse> responseObserver) {
						comportamientoHistorial.get().accept(request, responseObserver);
					}
				})
				.intercept(new ServerInterceptor() {
					@Override
					public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
							ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
						Context context = Context.current().withValue(USUARIO_CONTEXT_KEY, headers.get(USUARIO_METADATA_KEY));
						return Contexts.interceptCall(context, call, headers, next);
					}
				})
				.build()
				.start();
		registry.add("servicios.conversacion.grpc-host", () -> "localhost");
		registry.add("servicios.conversacion.grpc-port", servidorFalsoChatConversacion::getPort);
	}

	@AfterAll
	static void apagarServidorFalso() throws InterruptedException {
		servidorFalsoChatConversacion.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void chat_mensajeEntraPorWebSocketYVuelveTraducidoDesdeLaRespuestaGrpc() throws Exception {
		comportamientoChat.set((saliente, responseObserver) -> responseObserver.onNext(MensajeEntregado.newBuilder()
				.setId("1")
				.setRemitente(USUARIO_CONTEXT_KEY.get())
				.setDestinatario(saliente.getDestinatario())
				.setContenido(saliente.getContenido())
				.setEnviadoEn(Instant.parse("2026-09-18T20:53:47.441193Z").toString())
				.build()));

		BlockingQueue<String> mensajesRecibidos = new LinkedBlockingQueue<>();
		WebSocketSession session = new StandardWebSocketClient()
				.execute(new TextWebSocketHandler() {
					@Override
					protected void handleTextMessage(WebSocketSession s, TextMessage message) {
						mensajesRecibidos.add(message.getPayload());
					}
				}, "ws://localhost:" + port + "/ws/chat/mateo")
				.get(5, TimeUnit.SECONDS);

		try {
			session.sendMessage(new TextMessage("{\"destinatario\":\"ana\",\"contenido\":\"Hola!\"}"));

			String recibido = mensajesRecibidos.poll(5, TimeUnit.SECONDS);
			assertThat(recibido).isNotNull();
			assertThat(recibido).contains("\"remitente\":\"mateo\"");
			assertThat(recibido).contains("\"destinatario\":\"ana\"");
			assertThat(recibido).contains("\"contenido\":\"Hola!\"");
		} finally {
			session.close();
		}
	}

	@Test
	void chat_usuarioConFormatoInvalido_rechazaLaConexionSinLlegarAGrpc() {
		comportamientoChat.set((saliente, responseObserver) -> {
			throw new AssertionError("no deberia llegar a llamar a chat-conversacion");
		});

		assertThatThrownBy(() -> new StandardWebSocketClient()
				.execute(new TextWebSocketHandler() { }, "ws://localhost:" + port + "/ws/chat/ma")
				.get(5, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class);
	}

	@Test
	void historial_pasaPorGrpcYDevuelveLaPaginaComoJson() {
		comportamientoHistorial.set((request, responseObserver) -> {
			responseObserver.onNext(HistorialResponse.newBuilder()
					.addContent(MensajeEntregado.newBuilder()
							.setId("1")
							.setRemitente(request.getUsuarioA())
							.setDestinatario(request.getUsuarioB())
							.setContenido("Hola!")
							.setEnviadoEn(Instant.parse("2026-09-18T20:53:47.441193Z").toString())
							.build())
					.setPage(0)
					.setSize(20)
					.setTotalElements(1)
					.setTotalPages(1)
					.setFirst(true)
					.setLast(true)
					.setEmpty(false)
					.build());
			responseObserver.onCompleted();
		});

		ResponseEntity<Map> respuesta = restTemplate.getForEntity("/api/v1/conversaciones/mateo/ana", Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).containsEntry("totalElements", 1);
		List<?> contenido = (List<?>) respuesta.getBody().get("content");
		assertThat(contenido).hasSize(1);
		assertThat(((Map<?, ?>) contenido.get(0)).get("remitente")).isEqualTo("mateo");
	}

	@Test
	void historial_conversacionCaida_devuelve503() {
		comportamientoHistorial.set((request, responseObserver) ->
				responseObserver.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException()));

		ResponseEntity<Map> respuesta = restTemplate.getForEntity("/api/v1/conversaciones/mateo/ana", Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(respuesta.getBody()).containsEntry("type", "urn:problem-type:service-unavailable");
	}
}
