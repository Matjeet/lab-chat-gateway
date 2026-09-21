package com.arquetipo.demo;

import com.arquetipo.demo.common.auth.VerificadorTokenIdentidad;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DemoApplicationTests {

	// Con firebase.enabled=false (perfil de test) no hay ningun bean real de
	// VerificadorTokenIdentidad; AutenticacionExtractor necesita uno para poder construirse.
	@MockitoBean
	private VerificadorTokenIdentidad verificadorTokenIdentidad;

	@Test
	void contextLoads() {
	}

}
