package com.supermercado.carrito.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.messaging.config.RabbitMQConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica de verdad en un RabbitMQ de Testcontainers: una cola de prueba se
 * bindea al exchange carrito.events (como lo haria pedidos-service) y se lee
 * el mensaje real, que se valida contra el JSON Schema publicado en
 * docs/events/. Solo se mockea JwtDecoder, para no depender de Keycloak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CheckoutIntegrationTest {

    private static final String USUARIO_ID = "usuario-checkout-123";
    private static final Path SCHEMA = Path.of("docs/events/carrito.checkout-iniciado.schema.json");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange carritoEventsExchange;

    @Autowired
    private CarritoRepositoryPort carritoRepositoryPort;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private String colaDePrueba;

    @BeforeEach
    void declararColaDePrueba() {
        colaDePrueba = "test.checkout." + UUID.randomUUID();
        // Durable y sin autoDelete: RabbitMQ 4 rechaza colas transitorias no exclusivas, y receive() con
        // timeout usa un consumidor que borraria una cola autoDelete tras el primer mensaje. Se borra en @AfterEach.
        Queue cola = new Queue(colaDePrueba, true, false, false);
        rabbitAdmin.declareQueue(cola);
        Binding binding = BindingBuilder.bind(cola).to(carritoEventsExchange)
                .with(RabbitMQConfig.ROUTING_KEY_CHECKOUT_INICIADO);
        rabbitAdmin.declareBinding(binding);
    }

    @AfterEach
    void borrarColaDePrueba() {
        rabbitAdmin.deleteQueue(colaDePrueba);
    }

    @Test
    void iniciarCheckout_DeberiaPublicarUnEventoValidoSegunElSchema() throws Exception {
        Carrito carrito = guardarCarritoConUnItem(USUARIO_ID);

        MvcResult resultado = mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.checkoutId").exists())
                .andReturn();
        String checkoutId = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("checkoutId").asString();

        String json = recibirMensaje();
        assertThat(json).as("mensaje publicado en carrito.events").isNotNull();
        assertThat(validarContraSchema(json)).isEmpty();

        JsonNode evento = objectMapper.readTree(json);
        assertThat(evento.get("eventId").asString()).isEqualTo(checkoutId);
        assertThat(evento.get("correlationId").asString()).isEqualTo(checkoutId);
        assertThat(evento.get("eventType").asString()).isEqualTo("carrito.checkout-iniciado");
        assertThat(evento.get("producer").asString()).isEqualTo("carrito-service");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);

        JsonNode payload = evento.get("payload");
        assertThat(payload.get("carritoId").asString()).isEqualTo(carrito.getId().getValor().toString());
        assertThat(payload.get("usuarioId").asString()).isEqualTo(USUARIO_ID);
        assertThat(payload.get("total").decimalValue()).isEqualByComparingTo("3000");
        assertThat(payload.get("items")).hasSize(1);
        assertThat(payload.get("items").get(0).get("cantidad").asInt()).isEqualTo(2);
    }

    @Test
    void elValidadorDeSchema_DeberiaRechazarUnMensajeIncompleto() throws Exception {
        assertThat(validarContraSchema("{\"eventId\":\"no-es-un-evento-completo\"}")).isNotEmpty();
    }

    @Test
    void iniciarCheckout_NoDeberiaModificarElCarrito() throws Exception {
        guardarCarritoConUnItem(USUARIO_ID);

        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID))).andExpect(status().isAccepted());

        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID).orElseThrow();
        assertThat(carrito.getItems()).hasSize(1);
    }

    @Test
    void iniciarCheckout_DeberiaPublicarElMismoEventId_CuandoSeReintentaConLaMismaClave() throws Exception {
        guardarCarritoConUnItem(USUARIO_ID);
        String clave = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID)).header("Idempotency-Key", clave))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID)).header("Idempotency-Key", clave))
                .andExpect(status().isAccepted());

        String primero = objectMapper.readTree(recibirMensaje()).get("eventId").asString();
        String segundo = objectMapper.readTree(recibirMensaje()).get("eventId").asString();
        assertThat(primero).isEqualTo(segundo);
    }

    @Test
    void iniciarCheckout_DeberiaPublicarEventIdsDistintos_CuandoDosUsuariosUsanLaMismaClave() throws Exception {
        guardarCarritoConUnItem(USUARIO_ID);
        guardarCarritoConUnItem("otro-usuario-checkout");
        String clave = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID)).header("Idempotency-Key", clave))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe("otro-usuario-checkout")).header("Idempotency-Key", clave))
                .andExpect(status().isAccepted());

        String primero = objectMapper.readTree(recibirMensaje()).get("eventId").asString();
        String segundo = objectMapper.readTree(recibirMensaje()).get("eventId").asString();
        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void iniciarCheckout_DeberiaRetornar409YNoPublicarNada_CuandoElCarritoEstaVacio() throws Exception {
        carritoRepositoryPort.guardar(Carrito.crear(USUARIO_ID));

        mockMvc.perform(post("/api/carrito/checkout").with(jwtDe(USUARIO_ID)))
                .andExpect(status().isConflict());

        assertThat(recibirMensaje()).isNull();
    }

    @Test
    void iniciarCheckout_DeberiaRetornar401YNoPublicarNada_CuandoNoHayToken() throws Exception {
        guardarCarritoConUnItem(USUARIO_ID);

        mockMvc.perform(post("/api/carrito/checkout")).andExpect(status().isUnauthorized());

        assertThat(recibirMensaje()).isNull();
    }

    private String recibirMensaje() {
        Message mensaje = rabbitTemplate.receive(colaDePrueba, 1500);
        return mensaje == null ? null : new String(mensaje.getBody(), StandardCharsets.UTF_8);
    }

    private List<Error> validarContraSchema(String json) throws Exception {
        Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Files.readString(SCHEMA));
        return schema.validate(json, InputFormat.JSON);
    }

    private Carrito guardarCarritoConUnItem(String usuarioId) {
        Carrito carrito = Carrito.crear(usuarioId);
        carrito.agregarItem(UUID.randomUUID(), "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        return carritoRepositoryPort.guardar(carrito);
    }

    private static JwtRequestPostProcessor jwtDe(String usuarioId) {
        return jwt().jwt(j -> j.subject(usuarioId)).authorities(new SimpleGrantedAuthority("ROLE_cliente"));
    }
}
