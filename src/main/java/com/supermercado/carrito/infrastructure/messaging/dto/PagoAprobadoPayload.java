package com.supermercado.carrito.infrastructure.messaging.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO de deserializacion del payload de pago.aprobado, armado a partir del JSON Schema que publica pagos-service
 * (docs/events/pago.aprobado.schema.json). Solo los campos que carrito necesita; los demas se ignoran.
 */
public record PagoAprobadoPayload(UUID pagoId, String usuarioId, List<ItemDto> items) {

    public record ItemDto(UUID productoId, int cantidad) {
    }
}
