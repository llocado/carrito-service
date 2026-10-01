package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
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
class AgregarItemAlCarritoUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    @Mock
    private CatalogoProductosPort catalogoProductosPort;

    private AgregarItemAlCarritoUseCase agregarItemAlCarritoUseCase;

    @BeforeEach
    void setUp() {
        agregarItemAlCarritoUseCase = new AgregarItemAlCarritoUseCase(carritoRepositoryPort, catalogoProductosPort);
    }

    @Test
    void execute_DeberiaCrearCarritoYAgregarItem_CuandoElUsuarioNoTeniaCarritoTodavia() {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP");
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Carrito resultado = agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 2);

        assertThat(resultado.getItems()).hasSize(1);
        assertThat(resultado.getItems().get(0).getCantidad()).isEqualTo(2);
        verify(carritoRepositoryPort).guardar(any(Carrito.class));
    }

    @Test
    void execute_DeberiaIncrementarCantidad_CuandoElCarritoYaTeniaEseProducto() {
        Carrito carritoExistente = Carrito.crear(USUARIO_ID);
        carritoExistente.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP");
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoExistente));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Carrito resultado = agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 3);

        assertThat(resultado.getItems()).hasSize(1);
        assertThat(resultado.getItems().get(0).getCantidad()).isEqualTo(5);
    }

    @Test
    void execute_DeberiaLanzarExcepcion_CuandoProductosServiceNoConoceElProducto() {
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 2))
                .isInstanceOf(ProductoNoEncontradoException.class);

        verify(carritoRepositoryPort, org.mockito.Mockito.never()).guardar(any(Carrito.class));
    }
}
