package com.supermercado.carrito.infrastructure.messaging.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
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
    public static final String EXCHANGE_PAGOS_EVENTS = "pagos.events";
    public static final String ROUTING_KEY_PAGO_APROBADO = "pago.aprobado";
    public static final String QUEUE_PAGO_APROBADO = "carrito.pago-aprobado";
    private static final String DLX_CARRITO = "carrito.dlx";
    private static final String DLQ_PAGO_APROBADO = "carrito.pago-aprobado.dlq";

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TopicExchange carritoEventsExchange() {
        return new TopicExchange(EXCHANGE_CARRITO_EVENTS);
    }

    /** pagos.events es de pagos-service; se declara igual aqui (mismas propiedades) para poder enlazar la cola. */
    @Bean
    public TopicExchange pagosEventsExchange() {
        return new TopicExchange(EXCHANGE_PAGOS_EVENTS);
    }

    @Bean
    public DirectExchange carritoDeadLetterExchange() {
        return new DirectExchange(DLX_CARRITO);
    }

    @Bean
    public Queue pagoAprobadoDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_PAGO_APROBADO).build();
    }

    @Bean
    public Binding pagoAprobadoDeadLetterBinding() {
        return BindingBuilder.bind(pagoAprobadoDeadLetterQueue()).to(carritoDeadLetterExchange()).with(DLQ_PAGO_APROBADO);
    }

    @Bean
    public Queue pagoAprobadoQueue() {
        return QueueBuilder.durable(QUEUE_PAGO_APROBADO)
                .withArgument("x-dead-letter-exchange", DLX_CARRITO)
                .withArgument("x-dead-letter-routing-key", DLQ_PAGO_APROBADO)
                .build();
    }

    @Bean
    public Binding pagoAprobadoBinding() {
        return BindingBuilder.bind(pagoAprobadoQueue()).to(pagosEventsExchange()).with(ROUTING_KEY_PAGO_APROBADO);
    }

    /**
     * Los reintentos son para fallos transitorios. No se reintenta un mensaje que el listener ya rechazo de forma
     * definitiva (AmqpRejectAndDontRequeueException, a veces envuelta en otra excepcion) ni uno que no se puede
     * convertir (por ejemplo, JSON ilegible): van directo a la DLQ.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer noReintentarRechazosDefinitivos() {
        return settings -> settings.setExceptionPredicate(error -> !esIrrecuperable(error));
    }

    private static boolean esIrrecuperable(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause() == actual ? null : actual.getCause()) {
            if (actual instanceof AmqpRejectAndDontRequeueException
                    || actual instanceof MessageConversionException
                    || actual instanceof org.springframework.messaging.converter.MessageConversionException
                    || actual instanceof ClassCastException
                    || actual instanceof NoSuchMethodException) {
                return true;
            }
        }
        return false;
    }
}
