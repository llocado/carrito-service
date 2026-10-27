package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.domain.exception.CantidadMaximaExcedidaException;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.domain.exception.StockInsuficienteException;
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

    @Mock
    private CatalogoProductosPort catalogoProductosPort;

    private ActualizarCantidadItemUseCase actualizarCantidadItemUseCase;

    @BeforeEach
    void setUp() {
        actualizarCantidadItemUseCase = new ActualizarCantidadItemUseCase(carritoRepositoryPort, catalogoProductosPort);
    }

    @Test
    void execute_DeberiaActualizarLaCantidad_CuandoElItemExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto(10, true)));

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

    @Test
    void execute_DeberiaRechazarSubirLaCantidadPorEncimaDelStock_YNoModificarElCarrito() {
        Carrito carrito = carritoConItem(2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto(5, true)));

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 6))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 5 disponible");

        assertThat(carrito.cantidadDe(PRODUCTO_ID)).isEqualTo(2);
        verify(carritoRepositoryPort, never()).guardar(any(Carrito.class));
    }

    @Test
    void execute_DeberiaAceptarSubirHastaExactamenteElStock() {
        Carrito carrito = carritoConItem(2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto(5, true)));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 5).cantidadDe(PRODUCTO_ID)).isEqualTo(5);
    }

    @Test
    void execute_NoDeberiaConsultarElCatalogo_CuandoLaCantidadBajaOQuedaIgual() {
        Carrito carrito = carritoConItem(5);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 1);
        actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 1);

        verifyNoInteractions(catalogoProductosPort);
    }

    @Test
    void execute_NoDeberiaConsultarElCatalogo_CuandoElProductoNoEstaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 3))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);

        verifyNoInteractions(catalogoProductosPort);
    }

    @Test
    void execute_DeberiaRechazarSubir_CuandoElProductoYaNoExisteEnElCatalogo() {
        Carrito carrito = carritoConItem(2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 3))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void execute_DeberiaRechazarSubir_CuandoElProductoEstaInactivo() {
        Carrito carrito = carritoConItem(2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto(50, false)));

        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 3))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 0 disponible");
    }

    @Test
    void execute_DeberiaRechazarMasDelMaximoDeUnidades_SinConsultarNada() {
        assertThatThrownBy(() -> actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, Carrito.MAX_UNIDADES_POR_PRODUCTO + 1))
                .isInstanceOf(CantidadMaximaExcedidaException.class);

        verify(carritoRepositoryPort, never()).buscarPorUsuarioId(any());
        verify(catalogoProductosPort, never()).buscarProducto(any());
        verify(carritoRepositoryPort, never()).guardar(any(Carrito.class));
    }

    @Test
    void execute_DeberiaAceptarSubirHastaExactamenteElMaximo_CuandoHayStock() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoConItem(2)));
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto(500, true)));
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Carrito resultado = actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, Carrito.MAX_UNIDADES_POR_PRODUCTO);

        assertThat(resultado.cantidadDe(PRODUCTO_ID)).isEqualTo(100);
    }

    private static Carrito carritoConItem(int cantidad) {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", cantidad);
        return carrito;
    }

    private static ProductoSnapshot producto(int stock, boolean activo) {
        return new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", stock, activo);
    }
}
