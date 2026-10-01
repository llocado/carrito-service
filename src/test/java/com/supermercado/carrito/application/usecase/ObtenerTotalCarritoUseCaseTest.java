package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObtenerTotalCarritoUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    private ObtenerTotalCarritoUseCase obtenerTotalCarritoUseCase;

    @BeforeEach
    void setUp() {
        obtenerTotalCarritoUseCase = new ObtenerTotalCarritoUseCase(carritoRepositoryPort);
    }

    @Test
    void execute_DeberiaSumarPrecioPorCantidadDeTodosLosItems_CuandoElCarritoTieneVarios() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(UUID.randomUUID(), "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        carrito.agregarItem(UUID.randomUUID(), "Leche Entera", new BigDecimal("1200"), "CLP", 3);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        BigDecimal total = obtenerTotalCarritoUseCase.execute(USUARIO_ID);

        assertThat(total).isEqualByComparingTo("6600");
    }

    @Test
    void execute_DeberiaRetornarCero_CuandoElCarritoEstaVacio() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(Carrito.crear(USUARIO_ID)));

        assertThat(obtenerTotalCarritoUseCase.execute(USUARIO_ID)).isEqualByComparingTo("0");
    }

    @Test
    void execute_DeberiaRetornarCero_CuandoElUsuarioNoTieneCarritoTodavia() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(obtenerTotalCarritoUseCase.execute(USUARIO_ID)).isEqualByComparingTo("0");
    }
}
