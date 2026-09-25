package com.arquetipo.demo.conversacion.grpc;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
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
 * {@code Historial}, {@code ListaChats} y {@code CrearSolicitud} son unarios: {@link #historial},
 * {@link #listaChats} y {@link #crearSolicitud} son llamadas bloqueantes normales.
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
		log.debug(">> abrirChat(usuario='{}')", usuario);
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

		StreamObserver<MensajeEntrante> streamEntrante = new StreamObserver<>() {
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
		log.debug("<< abrirChat() -> OK");
		return streamEntrante;
	}

	public PageResponse<MensajeResponse> historial(String usuarioA, String usuarioB, int page, int size, String sort) {
		log.debug(">> historial(usuarioA='{}', usuarioB='{}', page={}, size={})", usuarioA, usuarioB, page, size);
		HistorialRequest peticion = HistorialRequest.newBuilder()
				.setUsuarioA(usuarioA)
				.setUsuarioB(usuarioB)
				.setPage(page)
				.setSize(size)
				.setSort(sort == null ? "" : sort)
				.build();

		try {
			HistorialResponse respuesta = blockingStub.historial(peticion);
			PageResponse<MensajeResponse> resultado = new PageResponse<>(
					respuesta.getContentList().stream().map(this::aMensajeResponse).toList(),
					respuesta.getPage(),
					respuesta.getSize(),
					respuesta.getTotalElements(),
					respuesta.getTotalPages(),
					respuesta.getFirst(),
					respuesta.getLast(),
					respuesta.getEmpty());
			log.debug("<< historial() -> OK, totalElements={}", resultado.totalElements());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito: el detalle real ya queda en traducir() (log.error).
			throw traducir(ex);
		}
	}

	public CursorPage<ChatResumen> listaChats(String usuario, String cursor, int size) {
		log.debug(">> listaChats(usuario='{}', conCursor={}, size={})", usuario, cursor != null && !cursor.isBlank(), size);
		ListaChatsRequest peticion = ListaChatsRequest.newBuilder()
				.setUsuario(usuario)
				.setCursor(cursor == null ? "" : cursor)
				.setSize(size)
				.build();

		try {
			ListaChatsResponse respuesta = blockingStub.listaChats(peticion);
			CursorPage<ChatResumen> resultado = new CursorPage<>(
					respuesta.getContentList().stream().map(this::aChatResumen).toList(),
					respuesta.getNextCursor(),
					respuesta.getHasMore());
			log.debug("<< listaChats() -> OK, chats={}, hasMore={}", resultado.content().size(), resultado.hasMore());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito, mismo criterio que en historial().
			throw traducir(ex);
		}
	}

	/**
	 * Crea una solicitud de chat de {@code solicitante} hacia {@code solicitado}. Ambos deben
	 * existir en {@code chat-registro} y no puede existir ya una solicitud entre ellos — las
	 * dos validaciones las hace {@code chat-conversacion}, este cliente solo traduce el error.
	 */
	public SolicitudChatResponse crearSolicitud(String solicitante, String solicitado) {
		log.debug(">> crearSolicitud(solicitante='{}', solicitado='{}')", solicitante, solicitado);
		CrearSolicitudRequest peticion = CrearSolicitudRequest.newBuilder()
				.setSolicitante(solicitante)
				.setSolicitado(solicitado)
				.build();

		try {
			SolicitudResponse respuesta = blockingStub.crearSolicitud(peticion);
			SolicitudChatResponse resultado = new SolicitudChatResponse(
					respuesta.getId(),
					respuesta.getSolicitante(),
					respuesta.getSolicitado(),
					respuesta.getAceptada(),
					Instant.parse(respuesta.getCreadaEn()));
			log.debug("<< crearSolicitud() -> OK, id={}", resultado.id());
			return resultado;
		} catch (StatusRuntimeException ex) {
			// Sin log de fin a proposito, mismo criterio que en historial().
			throw traducir(ex);
		}
	}

	private ChatResumen aChatResumen(com.arquetipo.demo.conversacion.grpc.ChatResumen resumen) {
		return new ChatResumen(resumen.getOtroUsuario(), aMensajeResponse(resumen.getUltimoMensaje()));
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
			case INVALID_ARGUMENT -> new ValidationException(ex.getStatus().getDescription(), List.of());
			case NOT_FOUND -> new ResourceNotFoundException(ex.getStatus().getDescription());
			case ALREADY_EXISTS -> new DuplicateResourceException(ex.getStatus().getDescription());
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
