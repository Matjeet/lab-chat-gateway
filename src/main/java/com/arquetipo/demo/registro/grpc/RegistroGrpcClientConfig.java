package com.arquetipo.demo.registro.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Canal y stub gRPC hacia {@code chat-registro}.
 *
 * <p>La creacion del {@link ManagedChannel} no bloquea ni conecta de inmediato (gRPC-Java
 * conecta de forma perezosa en la primera llamada), asi que el arranque del gateway no
 * depende de que {@code chat-registro} este ya levantado.
 *
 * <p>Sin TLS ({@code usePlaintext()}), igual que documenta chat-registro para su entorno
 * local/interno (ver {@code contrato-grpc-registro.md} §1); en produccion esta comunicacion
 * va dentro de la red interna, no expuesta al cliente.
 */
@Configuration
@EnableConfigurationProperties(RegistroGrpcProperties.class)
public class RegistroGrpcClientConfig {

	@Bean(destroyMethod = "shutdown")
	public ManagedChannel registroChannel(RegistroGrpcProperties propiedades) {
		return ManagedChannelBuilder
				.forAddress(propiedades.grpcHost(), propiedades.grpcPort())
				.usePlaintext()
				.build();
	}

	@Bean
	public RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub registroStub(ManagedChannel registroChannel) {
		return RegistroGrpcServiceGrpc.newBlockingStub(registroChannel);
	}
}
