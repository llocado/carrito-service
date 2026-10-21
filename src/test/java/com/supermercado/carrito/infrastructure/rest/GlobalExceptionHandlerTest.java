package com.supermercado.carrito.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleCarreraDeCreacion_DeberiaResponder409_CuandoPierdeLaUniqueDelItem() {
        var respuesta = handler.handleCarreraDeCreacion(violacion("uk_items_carrito_carrito_producto"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().mensaje()).contains("Intenta de nuevo");
    }

    @Test
    void handleCarreraDeCreacion_DeberiaResponder409_CuandoPierdeLaUniqueDelPrimerCarrito() {
        assertThat(handler.handleCarreraDeCreacion(violacion("uk_carritos_usuario_id")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleCarreraDeCreacion_DeberiaRelanzar_CuandoLaViolacionEsDeOtraConstraint() {
        DataIntegrityViolationException otra = violacion("ck_cantidad_positiva");

        assertThatThrownBy(() -> handler.handleCarreraDeCreacion(otra)).isSameAs(otra);
    }

    @Test
    void handleCarreraDeCreacion_DeberiaRelanzar_CuandoNoHayConstraintIdentificable() {
        DataIntegrityViolationException sinCausa = new DataIntegrityViolationException("sin causa");

        assertThatThrownBy(() -> handler.handleCarreraDeCreacion(sinCausa)).isSameAs(sinCausa);
    }

    private static DataIntegrityViolationException violacion(String constraint) {
        return new DataIntegrityViolationException("no se pudo ejecutar",
                new ConstraintViolationException("duplicado", new SQLException("x"), constraint));
    }
}
