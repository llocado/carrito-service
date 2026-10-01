package com.supermercado.carrito.infrastructure.rest.dto;

import jakarta.validation.constraints.Min;

public record ActualizarCantidadRequest(
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        int cantidad
) {
}
