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
class QuitarItemDelCarritoUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    private QuitarItemDelCarritoUseCase quitarItemDelCarritoUseCase;

    @BeforeEach
    void setUp() {
        quitarItemDelCarritoUseCase = new QuitarItemDelCarritoUseCase(carritoRepositoryPort);
    }

    @Test
    void execute_DeberiaQuitarElItem_CuandoExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Carrito resultado = quitarItemDelCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID);

        assertThat(resultado.getItems()).isEmpty();
    }

    @Test
    void execute_DeberiaLanzarExcepcion_CuandoElUsuarioNoTieneCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quitarItemDelCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);
    }
}
