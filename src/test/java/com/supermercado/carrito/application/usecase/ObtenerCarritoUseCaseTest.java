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
class ObtenerCarritoUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    private ObtenerCarritoUseCase obtenerCarritoUseCase;

    @BeforeEach
    void setUp() {
        obtenerCarritoUseCase = new ObtenerCarritoUseCase(carritoRepositoryPort);
    }

    @Test
    void execute_DeberiaRetornarElCarritoExistente_CuandoElUsuarioYaTieneUno() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(UUID.randomUUID(), "Manzana Fuji", new BigDecimal("1500"), "CLP", 1);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        Carrito resultado = obtenerCarritoUseCase.execute(USUARIO_ID);

        assertThat(resultado).isSameAs(carrito);
    }

    @Test
    void execute_DeberiaRetornarUnCarritoVacioSinPersistir_CuandoElUsuarioNoTieneCarritoTodavia() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        Carrito resultado = obtenerCarritoUseCase.execute(USUARIO_ID);

        assertThat(resultado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(resultado.getItems()).isEmpty();
    }
}
