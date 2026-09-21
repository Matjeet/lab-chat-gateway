package com.arquetipo.demo.conversacion.grpc;

import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unico punto donde el gateway habla con {@code chat-conversacion}. Traduce los DTO del
 * WebSocket/REST al mensaje proto (y viceversa) y convierte cualquier error gRPC en la misma
 * excepcion de dominio que lanzaria un servicio local — espejo de {@code RegistroGrpcClient}
 * para el feature de registro.
 *
 * <p>{@code Chat} es bidi streaming: {@link #abrirChat} no bloquea, abre el stream con la
 * cabecera de metadata {@code usuario} (ver {@code contrato-grpc-conversacion.md} §3.1) y
 * devuelve un {@link StreamObserver} para que el llamador (el WebSocket) mande mensajes
 * salientes; cada {@code MensajeEntregado} que llegue se traduce y se entrega a {@code receptor}.
 * {@code Historial} es unario: {@link #historial} es una llamada bloqueante normal.
 */
@Slf4j
@Component
public class ConversacionGrpcClient {

	private static final String NOMBRE_SERVICIO = "chat-conversacion";

	private static final Metadata.Key<String> USUARIO_METADATA_KEY =
			Metadata.Key.of("usuario", Metadata.ASCII_STRING_MARSHALLER);

	private final ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stub;
	private final ConversacionGrpcServiceGrpc.ConversacionGrpcServiceBlockingStub blockingStub;

	public ConversacionGrpcClient(
			ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stub,
			ConversacionGrpcServiceGrpc.ConversacionGrpcServiceBlockingStub blockingStub) {
		this.stub = stub;
		this.blockingStub = blockingStub;
	}

	/**
	 * Abre el stream {@code Chat} identificado como {@code usuario} y devuelve el
	 * {@link StreamObserver} por el que mandar los mensajes salientes de esa sesion. Cada
	 * {@code MensajeEntregado} que llegue (al remitente o al destinatario) se traduce y se
	 * entrega a {@code receptor}; los eventos de error/cierre del stream tambien se propagan
	 * a {@code receptor} tal cual, para que el llamador decida que hacer con su lado (p. ej.
	 * cerrar la sesion de WebSocket).
	 */
	public StreamObserver<MensajeEntrante> abrirChat(String usuario, StreamObserver<MensajeResponse> receptor) {
		Metadata cabeceras = new Metadata();
		cabeceras.put(USUARIO_METADATA_KEY, usuario);
		ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stubConUsuario =
				stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabeceras));

		StreamObserver<MensajeSaliente> streamSaliente = stubConUsuario.chat(new StreamObserver<>() {
			@Override
			public void onNext(MensajeEntregado value) {
				receptor.onNext(aMensajeResponse(value));
			}

			@Override
			public void onError(Throwable t) {
				receptor.onError(t);
			}

			@Override
			public void onCompleted() {
				receptor.onCompleted();
			}
		});

		return new StreamObserver<>() {
			@Override
			public void onNext(MensajeEntrante value) {
				streamSaliente.onNext(MensajeSaliente.newBuilder()
						.setDestinatario(value.destinatario())
						.setContenido(value.contenido())
						.build());
			}

			@Override
			public void onError(Throwable t) {
				streamSaliente.onError(t);
			}

			@Override
			public void onCompleted() {
				streamSaliente.onCompleted();
			}
		};
	}

	public PageResponse<MensajeResponse> historial(String usuarioA, String usuarioB, int page, int size, String sort) {
		HistorialRequest peticion = HistorialRequest.newBuilder()
				.setUsuarioA(usuarioA)
				.setUsuarioB(usuarioB)
				.setPage(page)
				.setSize(size)
				.setSort(sort == null ? "" : sort)
				.build();

		try {
			HistorialResponse respuesta = blockingStub.historial(peticion);
			return new PageResponse<>(
					respuesta.getContentList().stream().map(this::aMensajeResponse).toList(),
					respuesta.getPage(),
					respuesta.getSize(),
					respuesta.getTotalElements(),
					respuesta.getTotalPages(),
					respuesta.getFirst(),
					respuesta.getLast(),
					respuesta.getEmpty());
		} catch (StatusRuntimeException ex) {
			throw traducir(ex);
		}
	}

	private MensajeResponse aMensajeResponse(MensajeEntregado entregado) {
		return new MensajeResponse(
				entregado.getId(),
				entregado.getRemitente(),
				entregado.getDestinatario(),
				entregado.getContenido(),
				Instant.parse(entregado.getEnviadoEn()));
	}

	private RuntimeException traducir(StatusRuntimeException ex) {
		return switch (ex.getStatus().getCode()) {
			case UNAVAILABLE -> {
				log.error("No se pudo contactar con {} por gRPC", NOMBRE_SERVICIO, ex);
				yield new ServiceUnavailableException(NOMBRE_SERVICIO);
			}
			default -> {
				log.error("Fallo inesperado llamando a {} por gRPC (codigo={})",
						NOMBRE_SERVICIO, ex.getStatus().getCode(), ex);
				yield new RuntimeException(
						"Ocurrio un error inesperado al comunicarse con " + NOMBRE_SERVICIO, ex);
			}
		};
	}
}
