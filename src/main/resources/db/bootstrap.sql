-- ============================================================================
--  Bootstrap de la base de datos centralizada para el microservicio chat-registro
-- ============================================================================
--  La instancia MySQL es COMPARTIDA por todos los microservicios del sistema.
--  Cada servicio tiene:
--    - su propio esquema (aqui: chat_registro)
--    - su propio usuario con permisos SOLO sobre ese esquema
--
--  Este script se ejecuta UNA sola vez, con un usuario administrador (root):
--
--    mysql -u root -p < src/main/resources/db/bootstrap.sql
--
--  El esquema de tablas (tabla `usuarios`, etc.) lo gestiona la aplicacion
--  via Hibernate (spring.jpa.hibernate.ddl-auto). Cuando el proyecto adopte
--  Flyway/Liquibase, cambiar ddl-auto a `validate` y versionar el DDL en
--  src/main/resources/db/migration.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS chat_registro
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Usuario propio del servicio (accesible desde localhost y desde contenedores).
CREATE USER IF NOT EXISTS 'chat_registro_svc'@'%'
  IDENTIFIED BY 'chat_registro_pw';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
  ON chat_registro.*
  TO 'chat_registro_svc'@'%';

FLUSH PRIVILEGES;
