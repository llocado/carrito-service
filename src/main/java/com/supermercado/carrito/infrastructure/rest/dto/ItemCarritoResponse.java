package com.supermercado.carrito.infrastructure.rest.dto;

import java.math.BigDecimal;

public record ItemCarritoResponse(
        String productoId,
        String nombre,
        BigDecimal precioUnitario,
        String moneda,
        int cantidad,
        BigDecimal subtotal
) {
}
