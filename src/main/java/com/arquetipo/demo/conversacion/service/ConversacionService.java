package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.grpc.ConversacionGrpcClient;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

/**
 * Orquesta el chat del lado del gateway: enruta la sesion de WebSocket y la consulta de
 * historial a {@code chat-conversacion} por gRPC. Sin logica de negocio propia — igual que
 * {@link com.arquetipo.demo.registro.service.RegistroService}, existe para mantener el mismo
 * flujo {@code Controller/Handler -> Service -> GrpcClient} del resto de features.
 */
@Service
public class ConversacionService {

	private final ConversacionGrpcClient grpcClient;

	public ConversacionService(ConversacionGrpcClient grpcClient) {
		this.grpcClient = grpcClient;
	}

	public StreamObserver<MensajeEntrante> abrirChat(String usuario, StreamObserver<MensajeResponse> receptor) {
		return grpcClient.abrirChat(usuario, receptor);
	}

	public PageResponse<MensajeResponse> historial(String usuarioA, String usuarioB, int page, int size, String sort) {
		return grpcClient.historial(usuarioA, usuarioB, page, size, sort);
	}
}
