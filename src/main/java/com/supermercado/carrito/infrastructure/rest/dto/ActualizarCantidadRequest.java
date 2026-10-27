package com.supermercado.carrito.infrastructure.rest.dto;

import com.supermercado.carrito.domain.model.Carrito;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ActualizarCantidadRequest(
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        @Max(value = Carrito.MAX_UNIDADES_POR_PRODUCTO, message = "La cantidad no puede superar 100 unidades por producto")
        int cantidad
) {
}
