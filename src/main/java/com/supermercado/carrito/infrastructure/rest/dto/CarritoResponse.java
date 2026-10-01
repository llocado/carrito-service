package com.supermercado.carrito.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarritoResponse(
        String id,
        String usuarioId,
        List<ItemCarritoResponse> items,
        BigDecimal total
) {
}
