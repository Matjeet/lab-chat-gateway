package com.arquetipo.demo.common.exception;

/**
 * Un error de validacion asociado a un campo concreto del cuerpo de la peticion.
 * Espejo del objeto {@code errors[]} del contrato REST (ver {@code docs/contratos-api.md}).
 */
public record FieldError(String field, String message) {
}
