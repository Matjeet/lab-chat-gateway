package com.arquetipo.demo.registro.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica la traduccion DTO <-> proto y el mapeo de errores de {@link RegistroGrpcClient}
 * contra un servidor gRPC in-process (sin red real ni depender de que chat-registro este
 * levantado).
 */
class RegistroGrpcClientTest {

	private final List<ManagedChannel> canales = new ArrayList<>();
	private final List<Server> servidores = new ArrayList<>();

	@AfterEach
	void cerrarRecursosGrpc() throws InterruptedException {
		for (ManagedChannel canal : canales) {
			canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
		for (Server servidor : servidores) {
			servidor.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private RegistroGrpcClient clientePara(RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase servicio)
			throws IOException {
		String nombreServidor = "registro-grpc-test-" + System.nanoTime();
		Server servidor = InProcessServerBuilder.forName(nombreServidor)
				.directExecutor()
				.addService(servicio)
				.build()
				.start();
		servidores.add(servidor);

		ManagedChannel canal = InProcessChannelBuilder.forName(nombreServidor)
				.directExecutor()
				.build();
		canales.add(canal);

		return new RegistroGrpcClient(RegistroGrpcServiceGrpc.newBlockingStub(canal));
	}

	private static RegistroRequest request() {
		return new RegistroRequest("mateo", "mateo@example.com", "Passw0rd!23");
	}

	@Test
	void registrar_respuestaValida_seTraduceARegistroResponse() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void registrar(RegistrarUsuarioRequest req, StreamObserver<RegistrarUsuarioResponse> obs) {
				obs.onNext(RegistrarUsuarioResponse.newBuilder()
						.setId(1L)
						.setUsername(req.getUsername())
						.setEmail(req.getEmail())
						.setProveedor("password")
						.setActivo(true)
						.setCreatedAt("2026-09-08T20:53:47.441193Z")
						.build());
				obs.onCompleted();
			}
		});

		RegistroResponse respuesta = client.registrar(request());

		assertThat(respuesta.id()).isEqualTo(1L);
		assertThat(respuesta.username()).isEqualTo("mateo");
		assertThat(respuesta.proveedor()).isEqualTo("password");
		assertThat(respuesta.createdAt().toString()).isEqualTo("2026-09-08T20:53:47.441193Z");
	}

	@Test
	void registrar_alreadyExists_lanzaDuplicateResourceException() throws IOException {
		String mensajeGenerico = "No se pudo completar el registro con los datos proporcionados";
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void registrar(RegistrarUsuarioRequest req, StreamObserver<RegistrarUsuarioResponse> obs) {
				obs.onError(Status.ALREADY_EXISTS.withDescription(mensajeGenerico).asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.registrar(request()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage(mensajeGenerico);
	}

	@Test
	void registrar_invalidArgument_reconstruyeErroresPorCampo() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void registrar(RegistrarUsuarioRequest req, StreamObserver<RegistrarUsuarioResponse> obs) {
				obs.onError(Status.INVALID_ARGUMENT
						.withDescription("El cuerpo de la peticion no supero la validacion -> "
								+ "email: formato invalido; username: demasiado corto")
						.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.registrar(request()))
				.isInstanceOf(ValidationException.class)
				.satisfies(ex -> {
					var errores = ((ValidationException) ex).getErrores();
					assertThat(errores).hasSize(2);
					assertThat(errores.get(0).field()).isEqualTo("email");
					assertThat(errores.get(0).message()).isEqualTo("formato invalido");
					assertThat(errores.get(1).field()).isEqualTo("username");
				});
	}

	@Test
	void registrar_errorInterno_lanzaExcepcionGenerica() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void registrar(RegistrarUsuarioRequest req, StreamObserver<RegistrarUsuarioResponse> obs) {
				obs.onError(Status.INTERNAL.withDescription("boom").asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.registrar(request()))
				.isNotInstanceOf(DuplicateResourceException.class)
				.isNotInstanceOf(ValidationException.class);
	}

	@Test
	void buscarUsuarioPorUid_respuestaValida_seTraduceAUsuarioResponse() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void buscarUsuarioPorUid(BuscarUsuarioPorUidRequest req, StreamObserver<BuscarUsuarioPorUidResponse> obs) {
				obs.onNext(BuscarUsuarioPorUidResponse.newBuilder()
						.setUsername("mateo")
						.setEmail("mateo@example.com")
						.build());
				obs.onCompleted();
			}
		});

		UsuarioResponse respuesta = client.buscarUsuarioPorUid("uid-mateo");

		assertThat(respuesta.username()).isEqualTo("mateo");
		assertThat(respuesta.email()).isEqualTo("mateo@example.com");
	}

	@Test
	void buscarUsuarioPorUid_sinUsuarioConEseUid_lanzaResourceNotFoundException() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void buscarUsuarioPorUid(BuscarUsuarioPorUidRequest req, StreamObserver<BuscarUsuarioPorUidResponse> obs) {
				obs.onError(Status.NOT_FOUND.withDescription("Usuario no encontrado").asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.buscarUsuarioPorUid("uid-inexistente"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void buscarUsuarioPorUid_serviceCaido_lanzaServiceUnavailableException() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void buscarUsuarioPorUid(BuscarUsuarioPorUidRequest req, StreamObserver<BuscarUsuarioPorUidResponse> obs) {
				obs.onError(Status.UNAVAILABLE.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.buscarUsuarioPorUid("uid-mateo"))
				.isInstanceOf(com.arquetipo.demo.common.exception.ServiceUnavailableException.class);
	}

	@Test
	void existeUsername_usernameEnUso_devuelveTrue() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void existeUsername(ExisteUsernameRequest req, StreamObserver<ExisteUsernameResponse> obs) {
				obs.onNext(ExisteUsernameResponse.newBuilder().setExiste(true).build());
				obs.onCompleted();
			}
		});

		assertThat(client.existeUsername("mateo")).isTrue();
	}

	@Test
	void existeUsername_usernameLibre_devuelveFalse() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void existeUsername(ExisteUsernameRequest req, StreamObserver<ExisteUsernameResponse> obs) {
				obs.onNext(ExisteUsernameResponse.newBuilder().setExiste(false).build());
				obs.onCompleted();
			}
		});

		assertThat(client.existeUsername("libre")).isFalse();
	}

	@Test
	void existeUsername_usernameVacio_lanzaValidationException() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void existeUsername(ExisteUsernameRequest req, StreamObserver<ExisteUsernameResponse> obs) {
				obs.onError(Status.INVALID_ARGUMENT.withDescription("username es obligatorio").asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.existeUsername(""))
				.isInstanceOf(ValidationException.class)
				.hasMessage("username es obligatorio");
	}

	@Test
	void existeUsername_serviceCaido_lanzaServiceUnavailableException() throws IOException {
		RegistroGrpcClient client = clientePara(new RegistroGrpcServiceGrpc.RegistroGrpcServiceImplBase() {
			@Override
			public void existeUsername(ExisteUsernameRequest req, StreamObserver<ExisteUsernameResponse> obs) {
				obs.onError(Status.UNAVAILABLE.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.existeUsername("mateo"))
				.isInstanceOf(com.arquetipo.demo.common.exception.ServiceUnavailableException.class);
	}
}
