package com.arquetipo.demo.conversacion.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica la traduccion DTO <-> proto y el mapeo de errores de {@link ConversacionGrpcClient}
 * contra un servidor gRPC in-process, igual patron que {@code RegistroGrpcClientTest}.
 */
class ConversacionGrpcClientTest {

	private static final Metadata.Key<String> USUARIO_METADATA_KEY =
			Metadata.Key.of("usuario", Metadata.ASCII_STRING_MARSHALLER);
	private static final Context.Key<String> USUARIO_CONTEXT_KEY = Context.key("usuario");

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

	private ConversacionGrpcClient clientePara(ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase servicio)
			throws IOException {
		String nombreServidor = "conversacion-grpc-test-" + System.nanoTime();
		Server servidor = InProcessServerBuilder.forName(nombreServidor)
				.directExecutor()
				.addService(servicio)
				.intercept(new ServerInterceptor() {
					@Override
					public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
							ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
						String usuario = headers.get(USUARIO_METADATA_KEY);
						Context context = Context.current().withValue(USUARIO_CONTEXT_KEY, usuario);
						return Contexts.interceptCall(context, call, headers, next);
					}
				})
				.build()
				.start();
		servidores.add(servidor);

		ManagedChannel canal = InProcessChannelBuilder.forName(nombreServidor)
				.directExecutor()
				.build();
		canales.add(canal);

		return new ConversacionGrpcClient(
				ConversacionGrpcServiceGrpc.newStub(canal),
				ConversacionGrpcServiceGrpc.newBlockingStub(canal));
	}

	@Test
	void abrirChat_mandaLaCabeceraUsuarioYTraduceLoQueLlegaPorElStream() throws IOException, InterruptedException {
		CountDownLatch recibido = new CountDownLatch(1);
		List<MensajeResponse> mensajesRecibidos = new ArrayList<>();

		ConversacionGrpcClient client = clientePara(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
			@Override
			public StreamObserver<MensajeSaliente> chat(StreamObserver<MensajeEntregado> responseObserver) {
				String usuario = USUARIO_CONTEXT_KEY.get();
				return new StreamObserver<>() {
					@Override
					public void onNext(MensajeSaliente saliente) {
						responseObserver.onNext(MensajeEntregado.newBuilder()
								.setId("1")
								.setRemitente(usuario)
								.setDestinatario(saliente.getDestinatario())
								.setContenido(saliente.getContenido())
								.setEnviadoEn("2026-09-18T20:53:47.441193Z")
								.build());
					}

					@Override
					public void onError(Throwable t) { }

					@Override
					public void onCompleted() {
						responseObserver.onCompleted();
					}
				};
			}
		});

		StreamObserver<MensajeEntrante> streamSaliente = client.abrirChat("mateo", new StreamObserver<>() {
			@Override
			public void onNext(MensajeResponse value) {
				mensajesRecibidos.add(value);
				recibido.countDown();
			}

			@Override
			public void onError(Throwable t) { }

			@Override
			public void onCompleted() { }
		});

		streamSaliente.onNext(new MensajeEntrante("ana", "Hola!"));

		assertThat(recibido.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(mensajesRecibidos).hasSize(1);
		MensajeResponse recibidoTraducido = mensajesRecibidos.get(0);
		assertThat(recibidoTraducido.remitente()).isEqualTo("mateo");
		assertThat(recibidoTraducido.destinatario()).isEqualTo("ana");
		assertThat(recibidoTraducido.contenido()).isEqualTo("Hola!");
		assertThat(recibidoTraducido.enviadoEn()).isEqualTo(Instant.parse("2026-09-18T20:53:47.441193Z"));
	}

	@Test
	void abrirChat_elServidorCompleta_propagaOnCompletedAlReceptor() throws IOException, InterruptedException {
		CountDownLatch completado = new CountDownLatch(1);

		ConversacionGrpcClient client = clientePara(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
			@Override
			public StreamObserver<MensajeSaliente> chat(StreamObserver<MensajeEntregado> responseObserver) {
				responseObserver.onCompleted();
				return new StreamObserver<>() {
					@Override
					public void onNext(MensajeSaliente value) { }

					@Override
					public void onError(Throwable t) { }

					@Override
					public void onCompleted() { }
				};
			}
		});

		client.abrirChat("mateo", new StreamObserver<>() {
			@Override
			public void onNext(MensajeResponse value) { }

			@Override
			public void onError(Throwable t) { }

			@Override
			public void onCompleted() {
				completado.countDown();
			}
		});

		assertThat(completado.await(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void historial_respuestaValida_seTraduceAPageResponse() throws IOException {
		ConversacionGrpcClient client = clientePara(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
			@Override
			public void historial(HistorialRequest request, StreamObserver<HistorialResponse> responseObserver) {
				responseObserver.onNext(HistorialResponse.newBuilder()
						.addContent(MensajeEntregado.newBuilder()
								.setId("1")
								.setRemitente(request.getUsuarioA())
								.setDestinatario(request.getUsuarioB())
								.setContenido("Hola!")
								.setEnviadoEn("2026-09-18T20:53:47.441193Z")
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
			}
		});

		PageResponse<MensajeResponse> pagina = client.historial("mateo", "ana", 0, 20, "enviadoEn,asc");

		assertThat(pagina.content()).hasSize(1);
		assertThat(pagina.content().get(0).remitente()).isEqualTo("mateo");
		assertThat(pagina.totalElements()).isEqualTo(1);
		assertThat(pagina.first()).isTrue();
		assertThat(pagina.last()).isTrue();
		assertThat(pagina.empty()).isFalse();
	}

	@Test
	void historial_serviceConversacionCaido_lanzaServiceUnavailableException() throws IOException {
		ConversacionGrpcClient client = clientePara(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
			@Override
			public void historial(HistorialRequest request, StreamObserver<HistorialResponse> responseObserver) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.historial("mateo", "ana", 0, 20, ""))
				.isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void historial_errorInterno_lanzaExcepcionGenerica() throws IOException {
		ConversacionGrpcClient client = clientePara(new ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase() {
			@Override
			public void historial(HistorialRequest request, StreamObserver<HistorialResponse> responseObserver) {
				responseObserver.onError(Status.INTERNAL.withDescription("boom").asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.historial("mateo", "ana", 0, 20, ""))
				.isNotInstanceOf(ServiceUnavailableException.class);
	}
}
