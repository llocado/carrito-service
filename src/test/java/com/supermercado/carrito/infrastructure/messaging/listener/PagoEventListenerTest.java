package com.supermercado.carrito.infrastructure.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.supermercado.carrito.application.usecase.DescontarItemsCompradosUseCase;
import com.supermercado.carrito.domain.model.ItemComprado;
import com.supermercado.carrito.infrastructure.messaging.dto.EventEnvelope;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PagoEventListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final String ITEM = "{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":2}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private DescontarItemsCompradosUseCase descontarItemsComprados;

    private PagoEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PagoEventListener(descontarItemsComprados, objectMapper);
    }

    @Test
    void deberiaDelegarEnElCasoDeUso_CuandoElPayloadEsValido() {
        listener.escucharPagoAprobado(envelope(payload("\"u1\"", "[" + ITEM + "]")));

        verify(descontarItemsComprados).execute(EVENT_ID, "u1", List.of(new ItemComprado(PRODUCTO_ID, 2)));
    }

    @Test
    void deberiaRechazar_CuandoElPayloadEstaVacio() {
        assertRechazado(objectMapper.readTree("{}"));
    }

    @Test
    void deberiaAceptarUsuarioIdDeExactamente100Caracteres_YRechazarUnoDe101() {
        listener.escucharPagoAprobado(envelope(payload("\"" + "u".repeat(100) + "\"", "[" + ITEM + "]")));
        verify(descontarItemsComprados).execute(any(), any(), any());
        clearInvocations(descontarItemsComprados);

        assertRechazado(payload("\"" + "u".repeat(101) + "\"", "[" + ITEM + "]"));
        assertRechazado(payload("\" \"", "[" + ITEM + "]"));
        assertRechazado(payload("null", "[" + ITEM + "]"));
    }

    @Test
    void deberiaAceptarExactamente500Items_YRechazar501() {
        listener.escucharPagoAprobado(envelope(payload("\"u1\"", items(PagoEventListener.MAX_ITEMS))));
        verify(descontarItemsComprados).execute(any(), any(), any());
        clearInvocations(descontarItemsComprados);

        assertRechazado(payload("\"u1\"", items(PagoEventListener.MAX_ITEMS + 1)));
    }

    @Test
    void deberiaRechazar_CuandoNoHayItemsOUnItemEsInvalido() {
        assertRechazado(payload("\"u1\"", "[]"));
        assertRechazado(payload("\"u1\"", "null"));
        assertRechazado(payload("\"u1\"", "[null]"));
        assertRechazado(payload("\"u1\"", "[{\"cantidad\":2}]"));
        assertRechazado(payload("\"u1\"", "[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":0}]"));
        assertRechazado(payload("\"u1\"", "[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":-1}]"));
    }

    @Test
    void deberiaRechazar_CuandoUnCampoTieneElTipoIncorrecto() {
        // Items validos: asi lo unico invalido es el tipo de pagoId y el test no pasa por la regla "sin items".
        assertRechazado(objectMapper.readTree("{\"pagoId\":\"no-es-uuid\",\"usuarioId\":\"u1\",\"items\":[" + ITEM + "]}"));
    }

    @Test
    void deberiaRechazar_CuandoElEnvelopeEstaIncompleto() {
        EventEnvelope sinId = new EventEnvelope(null, "pago.aprobado", 1, Instant.now(), "pagos-service", null, payload("\"u1\"", "[" + ITEM + "]"));
        EventEnvelope sinPayload = new EventEnvelope(EVENT_ID, "pago.aprobado", 1, Instant.now(), "pagos-service", null, null);

        assertThatThrownBy(() -> listener.escucharPagoAprobado(sinId)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.escucharPagoAprobado(sinPayload)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.escucharPagoAprobado(null)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(descontarItemsComprados, never()).execute(any(), any(), any());
    }

    @Test
    void deberiaPropagarElErrorTalCual_CuandoElFalloEsTransitorio() {
        IllegalStateException transitorio = new IllegalStateException("base de datos no disponible");
        doThrow(transitorio).when(descontarItemsComprados).execute(any(), any(), any());

        assertThatThrownBy(() -> listener.escucharPagoAprobado(envelope(payload("\"u1\"", "[" + ITEM + "]")))).isSameAs(transitorio);
    }

    @Test
    void deberiaLimpiarElMdc_AlTerminar() {
        listener.escucharPagoAprobado(envelope(payload("\"u1\"", "[" + ITEM + "]")));

        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    private void assertRechazado(JsonNode payload) {
        assertThatThrownBy(() -> listener.escucharPagoAprobado(envelope(payload))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(descontarItemsComprados, never()).execute(any(), any(), any());
    }

    private static String items(int n) {
        return "[" + String.join(",", Collections.nCopies(n, ITEM)) + "]";
    }

    private JsonNode payload(String usuarioId, String items) {
        return objectMapper.readTree("{\"pagoId\":\"" + UUID.randomUUID() + "\",\"pedidoId\":\"" + UUID.randomUUID()
                + "\",\"usuarioId\":" + usuarioId + ",\"items\":" + items + "}");
    }

    private EventEnvelope envelope(JsonNode payload) {
        return new EventEnvelope(EVENT_ID, "pago.aprobado", 1, Instant.now(), "pagos-service", null, payload);
    }
}
