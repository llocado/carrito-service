package com.supermercado.carrito.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload de carrito.checkout-iniciado; contrato en docs/events/carrito.checkout-iniciado.schema.json. */
public record CarritoCheckoutIniciadoPayload(
        UUID carritoId,
        String usuarioId,
        List<ItemEventoDto> items,
        BigDecimal total
) {

    public record ItemEventoDto(
            UUID productoId,
            String nombre,
            BigDecimal precioUnitario,
            String moneda,
            int cantidad
    ) {
    }
}
