package com.arquetipo.demo.notificacion.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica la traduccion DTO <-> proto y el mapeo de errores de {@link NotificacionGrpcClient}
 * contra un servidor gRPC in-process, igual patron que {@code RegistroGrpcClientTest}.
 */
class NotificacionGrpcClientTest {

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

	private NotificacionGrpcClient clientePara(NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase servicio)
			throws IOException {
		String nombreServidor = "notificacion-grpc-test-" + System.nanoTime();
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

		return new NotificacionGrpcClient(NotificacionGrpcServiceGrpc.newBlockingStub(canal));
	}

	@Test
	void listaNotificaciones_respuestaValida_seTraduceAPageResponse() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void listaNotificaciones(ListaNotificacionesRequest request,
					StreamObserver<ListaNotificacionesResponse> responseObserver) {
				responseObserver.onNext(ListaNotificacionesResponse.newBuilder()
						.addContent(NotificacionItem.newBuilder()
								.setId(1L)
								.setRemitente("mateo")
								.setTipo("solicitud")
								.setLeida(false)
								.setCreatedAt("2026-09-25T20:53:47.441193Z")
								.setMeta("{\"aceptada\":false,\"pendiente\":true}")
								.setAvatarRemitente("https://cdn.example.com/avatares/mateo.png")
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

		PageResponse<NotificacionResponse> pagina = client.listaNotificaciones("ana", 0, 20, "createdAt,desc");

		assertThat(pagina.content()).hasSize(1);
		assertThat(pagina.content().get(0).id()).isEqualTo(1L);
		assertThat(pagina.content().get(0).remitente()).isEqualTo("mateo");
		assertThat(pagina.content().get(0).tipo()).isEqualTo("solicitud");
		assertThat(pagina.content().get(0).leida()).isFalse();
		assertThat(pagina.content().get(0).meta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
		assertThat(pagina.content().get(0).avatarRemitente()).isEqualTo("https://cdn.example.com/avatares/mateo.png");
		assertThat(pagina.totalElements()).isEqualTo(1);
	}

	@Test
	void listaNotificaciones_sinRemitente_devuelveNull() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void listaNotificaciones(ListaNotificacionesRequest request,
					StreamObserver<ListaNotificacionesResponse> responseObserver) {
				responseObserver.onNext(ListaNotificacionesResponse.newBuilder()
						.addContent(NotificacionItem.newBuilder()
								.setId(2L)
								.setTipo("sistema")
								.setLeida(true)
								.setCreatedAt("2026-09-25T20:53:47.441193Z")
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

		PageResponse<NotificacionResponse> pagina = client.listaNotificaciones("ana", 0, 20, "");

		assertThat(pagina.content().get(0).remitente()).isNull();
		assertThat(pagina.content().get(0).meta()).isNull();
		assertThat(pagina.content().get(0).avatarRemitente()).isNull();
	}

	@Test
	void listaNotificaciones_serviceCaido_lanzaServiceUnavailableException() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void listaNotificaciones(ListaNotificacionesRequest request,
					StreamObserver<ListaNotificacionesResponse> responseObserver) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.listaNotificaciones("ana", 0, 20, ""))
				.isInstanceOf(ServiceUnavailableException.class);
	}

	@Test
	void actualizarLeida_respuestaValida_seTraduceANotificacionResponse() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void actualizarLeida(ActualizarLeidaRequest request, StreamObserver<NotificacionItem> responseObserver) {
				responseObserver.onNext(NotificacionItem.newBuilder()
						.setId(request.getId())
						.setRemitente("mateo")
						.setTipo("solicitud")
						.setLeida(request.getLeida())
						.setCreatedAt("2026-09-25T20:53:47.441193Z")
						.setMeta("{\"aceptada\":false,\"pendiente\":true}")
						.setAvatarRemitente("https://cdn.example.com/avatares/mateo.png")
						.build());
				responseObserver.onCompleted();
			}
		});

		NotificacionResponse respuesta = client.actualizarLeida(1L, true);

		assertThat(respuesta.id()).isEqualTo(1L);
		assertThat(respuesta.leida()).isTrue();
		assertThat(respuesta.createdAt()).isEqualTo(Instant.parse("2026-09-25T20:53:47.441193Z"));
		assertThat(respuesta.meta()).isEqualTo("{\"aceptada\":false,\"pendiente\":true}");
		assertThat(respuesta.avatarRemitente()).isEqualTo("https://cdn.example.com/avatares/mateo.png");
	}

	@Test
	void actualizarLeida_idInexistente_lanzaResourceNotFoundException() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void actualizarLeida(ActualizarLeidaRequest request, StreamObserver<NotificacionItem> responseObserver) {
				responseObserver.onError(Status.NOT_FOUND
						.withDescription("Notificacion no encontrada")
						.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.actualizarLeida(999L, true))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void actualizarLeida_serviceCaido_lanzaServiceUnavailableException() throws IOException {
		NotificacionGrpcClient client = clientePara(new NotificacionGrpcServiceGrpc.NotificacionGrpcServiceImplBase() {
			@Override
			public void actualizarLeida(ActualizarLeidaRequest request, StreamObserver<NotificacionItem> responseObserver) {
				responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
			}
		});

		assertThatThrownBy(() -> client.actualizarLeida(1L, true))
				.isInstanceOf(ServiceUnavailableException.class);
	}
}
