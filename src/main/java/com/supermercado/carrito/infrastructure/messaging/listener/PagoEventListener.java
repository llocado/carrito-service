package com.supermercado.carrito.infrastructure.messaging.listener;

import com.supermercado.carrito.application.usecase.DescontarItemsCompradosUseCase;
import com.supermercado.carrito.domain.model.ItemComprado;
import com.supermercado.carrito.infrastructure.messaging.config.RabbitMQConfig;
import com.supermercado.carrito.infrastructure.messaging.dto.EventEnvelope;
import com.supermercado.carrito.infrastructure.messaging.dto.PagoAprobadoPayload;
import com.supermercado.carrito.infrastructure.messaging.dto.PagoAprobadoPayload.ItemDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Consume pago.aprobado de pagos-service para descontar del carrito lo comprado. Politica de errores (ver
 * spring.rabbitmq.listener.simple en application.yml): payload invalido => DLQ sin reintentos; fallo transitorio =>
 * se deja propagar y Spring reintenta 1 + 3 veces antes de la DLQ. Los logs llevan eventId y correlationId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoEventListener {

    static final int MAX_ITEMS = 500;
    static final int MAX_USUARIO_ID = 100;

    private final DescontarItemsCompradosUseCase descontarItemsCompradosUseCase;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAGO_APROBADO)
    public void escucharPagoAprobado(EventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            log.warn("Evento descartado a la DLQ: falta eventId o payload");
            throw new AmqpRejectAndDontRequeueException("Envelope invalido: falta eventId o payload");
        }

        UUID correlacion = envelope.correlationId() != null ? envelope.correlationId() : envelope.eventId();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlacion.toString())) {
            log.info("Evento recibido eventType={} eventId={}", envelope.eventType(), envelope.eventId());

            PagoAprobadoPayload payload = leerPayload(envelope);
            String motivo = validar(payload);
            if (motivo != null) {
                log.warn("Evento descartado a la DLQ eventId={}: payload invalido ({})", envelope.eventId(), motivo);
                throw new AmqpRejectAndDontRequeueException("Payload invalido: " + motivo);
            }

            List<ItemComprado> comprados = payload.items().stream().map(i -> new ItemComprado(i.productoId(), i.cantidad())).toList();
            try {
                descontarItemsCompradosUseCase.execute(envelope.eventId(), payload.usuarioId(), comprados);
            } catch (RuntimeException e) {
                log.warn("Fallo al procesar evento eventId={}: {}", envelope.eventId(), e.toString());
                throw e;
            }
        }
    }

    private PagoAprobadoPayload leerPayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), PagoAprobadoPayload.class);
        } catch (JacksonException e) {
            log.warn("Evento descartado a la DLQ eventId={}: payload no deserializable", envelope.eventId());
            throw new AmqpRejectAndDontRequeueException("Payload no deserializable", e);
        }
    }

    /** Retorna el motivo si el payload es invalido, o null si esta bien. */
    static String validar(PagoAprobadoPayload payload) {
        if (payload == null) return "payload nulo";
        if (payload.usuarioId() == null || payload.usuarioId().isBlank() || payload.usuarioId().length() > MAX_USUARIO_ID) {
            return "usuarioId ausente o demasiado largo";
        }
        return validarItems(payload.items());
    }

    private static String validarItems(List<ItemDto> items) {
        if (items == null || items.isEmpty()) return "sin items";
        if (items.size() > MAX_ITEMS) return "demasiados items";
        for (ItemDto item : items) {
            if (item == null) return "item nulo";
            if (item.productoId() == null) return "item sin productoId";
            if (item.cantidad() <= 0) return "item con cantidad invalida";
        }
        return null;
    }
}
