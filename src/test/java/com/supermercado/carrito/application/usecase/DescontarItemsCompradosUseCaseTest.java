package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.EventoProcesadoPort;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.ItemComprado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DescontarItemsCompradosUseCaseTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID MANZANA = UUID.randomUUID();
    private static final UUID PERA = UUID.randomUUID();
    private static final String USUARIO_ID = "usuario-1";

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    @Mock
    private EventoProcesadoPort eventoProcesadoPort;

    private DescontarItemsCompradosUseCase useCase;
    private Carrito carrito;

    @BeforeEach
    void setUp() {
        useCase = new DescontarItemsCompradosUseCase(carritoRepositoryPort, eventoProcesadoPort);
        carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(MANZANA, "Manzana", new BigDecimal("1500"), "CLP", 5);
        carrito.agregarItem(PERA, "Pera", new BigDecimal("1000"), "CLP", 1);
    }

    @Test
    void execute_DeberiaDescontarLoCompradoYGuardar_ConservandoLoDemas() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        useCase.execute(EVENT_ID, USUARIO_ID, List.of(new ItemComprado(MANZANA, 2), new ItemComprado(PERA, 1)));

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getProductoId()).isEqualTo(MANZANA);
        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(3);
        verify(carritoRepositoryPort).guardar(carrito);
        verify(eventoProcesadoPort).marcarComoProcesado(EVENT_ID);
    }

    @Test
    void execute_NoDeberiaHacerNada_CuandoElEventoYaFueProcesado() {
        when(eventoProcesadoPort.yaFueProcesado(EVENT_ID)).thenReturn(true);

        useCase.execute(EVENT_ID, USUARIO_ID, List.of(new ItemComprado(MANZANA, 2)));

        verify(carritoRepositoryPort, never()).buscarPorUsuarioId(any());
        verify(carritoRepositoryPort, never()).guardar(any());
        verify(eventoProcesadoPort, never()).marcarComoProcesado(any());
    }

    @Test
    void execute_DeberiaMarcarElEventoYNoGuardarNada_CuandoElUsuarioNoTieneCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        useCase.execute(EVENT_ID, USUARIO_ID, List.of(new ItemComprado(MANZANA, 2)));

        verify(carritoRepositoryPort, never()).guardar(any());
        verify(eventoProcesadoPort).marcarComoProcesado(EVENT_ID);
    }
}
