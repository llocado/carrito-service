package com.supermercado.carrito.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.usecase.DescontarItemsCompradosUseCase;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.ItemCarrito;
import com.supermercado.carrito.infrastructure.messaging.config.RabbitMQConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * pago.aprobado -> carrito contra RabbitMQ y PostgreSQL reales: se descuenta solo lo comprado y los mensajes
 * venenosos terminan en la DLQ sin bloquear la cola. Las esperas de reintento se acortan solo para que el test no tarde.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
class CarritoPagoIntegrationTest {

    private static final String DLQ = "carrito.pago-aprobado.dlq";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private CarritoRepositoryPort carritoRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private DescontarItemsCompradosUseCase descontarItemsCompradosUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void limpiarColas() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.QUEUE_PAGO_APROBADO, false);
        rabbitAdmin.purgeQueue(DLQ, false);
    }

    @Test
    void pagoAprobado_DeberiaDescontarSoloLoComprado_ConservandoLoQueSobraYLoNuevo() {
        String usuario = usuarioUnico();
        UUID manzana = UUID.randomUUID(), pera = UUID.randomUUID(), pan = UUID.randomUUID();
        sembrarCarrito(usuario, item(manzana, 5), item(pera, 1), item(pan, 4));

        // Compro 2 manzanas y la pera; el pan (agregado despues del checkout) no estaba en la compra.
        publicar(UUID.randomUUID(), pagoAprobado(usuario, "[" + compra(manzana, 2) + "," + compra(pera, 1) + "]"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<ItemCarrito> items = itemsDe(usuario);
            assertThat(items).hasSize(2);
            assertThat(items).filteredOn(i -> i.getProductoId().equals(manzana)).singleElement()
                    .satisfies(i -> assertThat(i.getCantidad()).isEqualTo(3));
            assertThat(items).filteredOn(i -> i.getProductoId().equals(pan)).singleElement()
                    .satisfies(i -> assertThat(i.getCantidad()).isEqualTo(4));
        });
    }

    @Test
    void elMismoPagoEntregadoDosVeces_DeberiaDescontarUnaSolaVez() throws Exception {
        String usuario = usuarioUnico();
        UUID manzana = UUID.randomUUID();
        sembrarCarrito(usuario, item(manzana, 5));
        UUID eventId = UUID.randomUUID();
        String cuerpo = pagoAprobado(usuario, "[" + compra(manzana, 2) + "]");

        publicar(eventId, cuerpo);
        publicar(eventId, cuerpo);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(itemsDe(usuario).get(0).getCantidad()).isEqualTo(3));
        Thread.sleep(1000);
        assertThat(itemsDe(usuario).get(0).getCantidad()).as("la reentrega no vuelve a descontar").isEqualTo(3);
    }

    @Test
    void pagoDeUnUsuarioSinCarrito_NoDeberiaFallarNiIrALaDlq() throws Exception {
        publicar(UUID.randomUUID(), pagoAprobado(usuarioUnico(), "[" + compra(UUID.randomUUID(), 1) + "]"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbitAdmin.getQueueInfo(RabbitMQConfig.QUEUE_PAGO_APROBADO).getMessageCount()).isZero());
        Thread.sleep(500);
        assertThat(rabbitAdmin.getQueueInfo(DLQ).getMessageCount()).isZero();
    }

    @Test
    void payloadInvalido_DeberiaIrALaDlqSinReintentos() {
        publicar(UUID.randomUUID(), "{\"pagoId\":\"" + UUID.randomUUID() + "\",\"usuarioId\":\"u1\",\"items\":[]}");

        assertThat(rabbitTemplate.receive(DLQ, 10_000)).as("mensaje en la DLQ").isNotNull();
        verify(descontarItemsCompradosUseCase, never()).execute(any(), any(), any());
    }

    @Test
    void jsonRoto_DeberiaIrALaDlq() {
        publicarCrudo("esto{no es json");

        assertThat(rabbitTemplate.receive(DLQ, 10_000)).as("mensaje en la DLQ").isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(RabbitMQConfig.QUEUE_PAGO_APROBADO).getMessageCount()).isZero();
    }

    @Test
    void falloTransitorioPersistente_DeberiaHacer1IntentoY3ReintentosYLuegoIrALaDlq() throws Exception {
        doThrow(new IllegalStateException("fallo transitorio simulado")).when(descontarItemsCompradosUseCase).execute(any(), any(), any());

        publicar(UUID.randomUUID(), pagoAprobado(usuarioUnico(), "[" + compra(UUID.randomUUID(), 1) + "]"));

        assertThat(rabbitTemplate.receive(DLQ, 10_000)).as("mensaje en la DLQ tras agotar reintentos").isNotNull();
        verify(descontarItemsCompradosUseCase, times(4)).execute(any(), any(), any());
        Thread.sleep(1500);
        verify(descontarItemsCompradosUseCase, times(4)).execute(any(), any(), any());
    }

    @Test
    void unMensajeVenenoso_NoDeberiaBloquearLaColaParaLosPagosValidosSiguientes() {
        publicar(UUID.randomUUID(), "{}");
        String usuario = usuarioUnico();
        UUID manzana = UUID.randomUUID();
        sembrarCarrito(usuario, item(manzana, 5));
        publicar(UUID.randomUUID(), pagoAprobado(usuario, "[" + compra(manzana, 5) + "]"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(itemsDe(usuario)).isEmpty());
        assertThat(rabbitTemplate.receive(DLQ, 5_000)).as("el venenoso quedo en la DLQ").isNotNull();
    }

    private void sembrarCarrito(String usuario, ItemCarrito... items) {
        Carrito carrito = Carrito.crear(usuario);
        for (ItemCarrito i : items) carrito.agregarItem(i.getProductoId(), i.getNombre(), i.getPrecioUnitario(), i.getMoneda(), i.getCantidad());
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> carritoRepositoryPort.guardar(carrito));
    }

    private List<ItemCarrito> itemsDe(String usuario) {
        return new TransactionTemplate(transactionManager).execute(s ->
                carritoRepositoryPort.buscarPorUsuarioId(usuario).orElseThrow().getItems());
    }

    private static ItemCarrito item(UUID productoId, int cantidad) {
        return ItemCarrito.de(productoId, "Producto", new BigDecimal("1000"), "CLP", cantidad);
    }

    private static String compra(UUID productoId, int cantidad) {
        return "{\"productoId\":\"" + productoId + "\",\"cantidad\":" + cantidad + "}";
    }

    private static String pagoAprobado(String usuario, String items) {
        return "{\"pagoId\":\"" + UUID.randomUUID() + "\",\"pedidoId\":\"" + UUID.randomUUID() + "\",\"carritoId\":\"" + UUID.randomUUID()
                + "\",\"usuarioId\":\"" + usuario + "\",\"monto\":1000,\"moneda\":\"CLP\",\"items\":" + items + "}";
    }

    private void publicar(UUID eventId, String payload) {
        publicarCrudo("{\"eventId\":\"" + eventId + "\",\"eventType\":\"pago.aprobado\",\"eventVersion\":1,"
                + "\"occurredAt\":\"2026-10-04T12:00:00Z\",\"producer\":\"pruebas\",\"payload\":" + payload + "}");
    }

    private void publicarCrudo(String cuerpo) {
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8)).setContentType("application/json").build();
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_PAGOS_EVENTS, RabbitMQConfig.ROUTING_KEY_PAGO_APROBADO, mensaje);
    }

    private static String usuarioUnico() {
        return "usuario-integ-" + UUID.randomUUID();
    }
}
