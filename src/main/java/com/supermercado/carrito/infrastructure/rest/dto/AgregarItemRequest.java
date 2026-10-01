package com.supermercado.carrito.infrastructure.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AgregarItemRequest(
        @NotNull(message = "El id de producto es obligatorio")
        UUID productoId,

        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        int cantidad
) {
}
