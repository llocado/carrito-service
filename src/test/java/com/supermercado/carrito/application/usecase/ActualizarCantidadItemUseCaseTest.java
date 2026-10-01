package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
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
class ActualizarCantidadItemUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    private ActualizarCantidadItemUseCase actualizarCantidadItemUseCase;

    @BeforeEach
    void setUp() {
        actualizarCantidadItemUseCase = new ActualizarCantidadItemUseCase(carritoRepositoryPort);
    }

    @Test
    void execute_DeberiaActualizarLaCantidad_CuandoElItemExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Carrito resultado = actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 10);

        assertThat(resultado.getItems().get(0).getCantidad()).isEqualTo(10);
    }

    @Test
    void execute_DeberiaLanzarExcepcion_CuandoElUsuarioNoTieneCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 10))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);
    }

    @Test
    void execute_DeberiaLanzarExcepcion_CuandoElProductoNoEstaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 10))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);
    }
}
