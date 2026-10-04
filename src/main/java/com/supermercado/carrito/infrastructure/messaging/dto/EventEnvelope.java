package com.supermercado.carrito.infrastructure.messaging.dto;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Envelope comun a todo evento del sistema (ver "Contrato de eventos" en el ROADMAP). */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        UUID correlationId,
        JsonNode payload
) {
}
