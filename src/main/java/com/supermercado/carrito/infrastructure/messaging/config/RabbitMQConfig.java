package com.supermercado.carrito.infrastructure.messaging.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ de carrito-service (ver "Contrato de eventos" en el
 * ROADMAP): un exchange topic propio, routing key = eventType. Cada consumidor
 * (hoy pedidos-service) arma su propia cola y la bindea a este exchange.
 * El exchange se declara de forma idempotente: pedidos-service tambien lo
 * declara, con las mismas propiedades.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_CARRITO_EVENTS = "carrito.events";
    public static final String ROUTING_KEY_CHECKOUT_INICIADO = "carrito.checkout-iniciado";

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TopicExchange carritoEventsExchange() {
        return new TopicExchange(EXCHANGE_CARRITO_EVENTS);
    }
}
