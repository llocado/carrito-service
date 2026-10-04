package com.supermercado.carrito.infrastructure.messaging.publisher;

import com.supermercado.carrito.application.port.CheckoutEventPublisherPort;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.ItemCarrito;
import com.supermercado.carrito.infrastructure.messaging.config.RabbitMQConfig;
import com.supermercado.carrito.infrastructure.messaging.dto.CarritoCheckoutIniciadoPayload;
import com.supermercado.carrito.infrastructure.messaging.dto.CarritoCheckoutIniciadoPayload.ItemEventoDto;
import com.supermercado.carrito.infrastructure.messaging.dto.EventEnvelope;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutEventPublisherAdapter implements CheckoutEventPublisherPort {

    private static final String PRODUCER = "carrito-service";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /** eventId y correlationId son el id del intento de checkout: sirve para rastrear el flujo completo. */
    @Override
    public void publicarCheckoutIniciado(Carrito carrito, UUID checkoutId) {
        CarritoCheckoutIniciadoPayload payload = new CarritoCheckoutIniciadoPayload(
                carrito.getId().getValor(),
                carrito.getUsuarioId(),
                carrito.getItems().stream().map(this::toEventoDto).toList(),
                carrito.calcularTotal()
        );

        EventEnvelope envelope = new EventEnvelope(
                checkoutId,
                RabbitMQConfig.ROUTING_KEY_CHECKOUT_INICIADO,
                1,
                Instant.now(),
                PRODUCER,
                checkoutId,
                objectMapper.valueToTree(payload)
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_CARRITO_EVENTS, RabbitMQConfig.ROUTING_KEY_CHECKOUT_INICIADO, envelope);

        log.info("Evento publicado eventType={} eventId={}", envelope.eventType(), envelope.eventId());
    }

    private ItemEventoDto toEventoDto(ItemCarrito item) {
        return new ItemEventoDto(
                item.getProductoId(), item.getNombre(), item.getPrecioUnitario(), item.getMoneda(), item.getCantidad());
    }
}
