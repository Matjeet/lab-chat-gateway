package com.arquetipo.demo.notificacion.service;

import com.arquetipo.demo.notificacion.grpc.NotificacionGrpcClient;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta las notificaciones del lado del gateway: enruta la lista de notificaciones y la
 * actualizacion de leida a {@code chat-notificaciones} por gRPC. Sin logica de negocio propia
 * — igual que {@code RegistroService}/{@code ConversacionService}, existe para mantener el
 * mismo flujo {@code Controller -> Service -> GrpcClient} del resto de features.
 */
@Slf4j
@Service
public class NotificacionService {

	private final NotificacionGrpcClient grpcClient;

	public NotificacionService(NotificacionGrpcClient grpcClient) {
		this.grpcClient = grpcClient;
	}

	public PageResponse<NotificacionResponse> listaNotificaciones(String receptor, int page, int size, String sort) {
		log.debug(">> listaNotificaciones(receptor='{}', page={}, size={})", receptor, page, size);
		PageResponse<NotificacionResponse> respuesta = grpcClient.listaNotificaciones(receptor, page, size, sort);
		log.debug("<< listaNotificaciones() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	public NotificacionResponse actualizarLeida(long id, boolean leida) {
		log.debug(">> actualizarLeida(id={}, leida={})", id, leida);
		NotificacionResponse respuesta = grpcClient.actualizarLeida(id, leida);
		log.debug("<< actualizarLeida() -> OK, id={}, leida={}", respuesta.id(), respuesta.leida());
		return respuesta;
	}
}
