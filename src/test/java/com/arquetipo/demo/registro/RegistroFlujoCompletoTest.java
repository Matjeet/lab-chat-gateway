package com.arquetipo.demo.registro;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquetipo.demo.common.auth.VerificadorTokenIdentidad;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioRequest;
import com.arquetipo.demo.registro.grpc.RegistrarUsuarioResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcServiceGrpc;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prueba el flujo completo que debe seguir SIEMPRE una peticion que entra al gateway:
 *
 * <ol>
 *   <li>Cliente -> gateway por REST.</li>
 *   <li>Gateway identifica el servicio destino y llama por gRPC.</li>
 *   <li>El servicio (aqui, un doble de chat-registro) procesa y responde por gRPC.</li>
 *   <li>El gateway traduce esa respuesta (o error) de vuelta a REST para el cliente.</li>
 * </ol>
 *
 * <p>A diferencia de {@code RegistroGrpcClientTest} (que prueba el cliente aislado con un
 * servidor in-process), aqui se levanta el contexto de Spring completo
 * ({@code Controller -> Service -> GrpcClient}) contra un servidor gRPC real por TCP, igual
 * que hablaria con {@code chat-registro} en un entorno real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RegistroFlujoCompletoTest {

	// Con firebase.enabled=false (perfil de test) no hay ningun bean real de
	// VerificadorTokenIdentidad; AutenticacionExtractor (UsuarioController) necesita uno para
	// poder construirse, aunque este test no ejercite ese endpoint.
	@MockitoBean
	private VerificadorTokenIdentidad verificadorTokenIdentidad;

	private static Server servidorFalsoChatRegistro;

	/** Comportamiento del doble de chat-registro; cada test lo reconfigura antes de llamar. */
	private static final AtomicReference<BiConsumer<RegistrarUsuarioRequest, StreamObserver<RegistrarUsuarioResponse>>>
			comportamiento = new AtomicReference<>();

	@DynamicPropertySource
	static void apuntarElGatewayAlServidorFalso(DynamicPropertyRegistry registry) throws IOException {
		servidorFalsoChatRegistro = ServerBuilder.forPort(0)
				.addService(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
					@Override
					public void registrar(RegistrarUsuarioRequest request,
							StreamObserver<RegistrarUsuarioResponse> responseObserver) {
						comportamiento.get().accept(request, responseObserver);
					}
				})
				.build()
				.start();
		registry.add("servicios.registro.grpc-host", () -> "localhost");
		registry.add("servicios.registro.grpc-port", servidorFalsoChatRegistro::getPort);
	}

	@AfterAll
	static void apagarServidorFalso() throws InterruptedException {
		servidorFalsoChatRegistro.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void registrar_llegaPorRestYVuelveTraducidoDesdeLaRespuestaGrpc() {
		comportamiento.set((req, obs) -> {
			obs.onNext(RegistrarUsuarioResponse.newBuilder()
					.setId(1L)
					.setUsername(req.getUsername())
					.setEmail(req.getEmail())
					.setProveedor("password")
					.setActivo(true)
					.setCreatedAt(Instant.parse("2026-09-08T20:53:47.441193Z").toString())
					.build());
			obs.onCompleted();
		});

		ResponseEntity<RegistroResponse> respuesta = restTemplate.postForEntity(
				"/api/v1/registro",
				new RegistroRequest("mateo", "mateo@example.com", "Passw0rd!23"),
				RegistroResponse.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(respuesta.getBody()).isNotNull();
		assertThat(respuesta.getBody().id()).isEqualTo(1L);
		assertThat(respuesta.getBody().username()).isEqualTo("mateo");
		assertThat(respuesta.getBody().proveedor()).isEqualTo("password");
	}

	@Test
	void registrar_errorGrpcAlreadyExists_vuelveComo409ConMensajeGenerico() {
		String mensajeGenerico = "No se pudo completar el registro con los datos proporcionados";
		comportamiento.set((req, obs) ->
				obs.onError(Status.ALREADY_EXISTS.withDescription(mensajeGenerico).asRuntimeException()));

		ResponseEntity<Map> respuesta = restTemplate.postForEntity(
				"/api/v1/registro",
				new RegistroRequest("mateo", "mateo@example.com", "Passw0rd!23"),
				Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(respuesta.getBody()).containsEntry("type", "urn:problem-type:duplicate-resource");
		assertThat(respuesta.getBody()).containsEntry("detail", mensajeGenerico);
	}

	@Test
	void registrar_errorGrpcUnavailable_vuelveComo503() {
		comportamiento.set((req, obs) -> obs.onError(Status.UNAVAILABLE.asRuntimeException()));

		ResponseEntity<Map> respuesta = restTemplate.postForEntity(
				"/api/v1/registro",
				new RegistroRequest("mateo", "mateo@example.com", "Passw0rd!23"),
				Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(respuesta.getBody()).containsEntry("type", "urn:problem-type:service-unavailable");
	}

	@Test
	void registrar_cuerpoInvalido_nuncaLlegaAGrpc_vuelveComo400() {
		// Comportamiento que fallaria el test si se llegara a invocar: prueba que el gateway
		// corta en su propia validacion (@Valid) antes de llamar por gRPC.
		comportamiento.set((req, obs) -> obs.onError(Status.INTERNAL
				.withDescription("no deberia llamarse: el gateway debio rechazar antes de esto")
				.asRuntimeException()));

		ResponseEntity<Map> respuesta = restTemplate.postForEntity(
				"/api/v1/registro",
				new RegistroRequest("m", "no-es-email", "corta"),
				Map.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody()).containsKey("errors");
	}
}
