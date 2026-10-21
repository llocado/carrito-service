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
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 100, true);
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

        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 100, true);
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

    @Test
    void execute_DeberiaAceptarLaCantidadIgualAlStock_YRechazarUnaUnidadMas() {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 5, true);
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());
        when(carritoRepositoryPort.guardar(any(Carrito.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 5).getItems().get(0).getCantidad()).isEqualTo(5);

        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 6))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 5 disponible");
    }

    @Test
    void execute_DeberiaContarLoQueYaHayEnElCarrito_AlValidarElStock() {
        Carrito carritoExistente = Carrito.crear(USUARIO_ID);
        carritoExistente.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 4);
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 5, true);
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoExistente));

        // 4 en el carrito + 2 = 6 > 5 disponibles
        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 2))
                .isInstanceOf(StockInsuficienteException.class);

        assertThat(carritoExistente.cantidadDe(PRODUCTO_ID)).as("no se modifica el carrito").isEqualTo(4);
        verify(carritoRepositoryPort, org.mockito.Mockito.never()).guardar(any(Carrito.class));
    }

    @Test
    void execute_DeberiaRechazarSinDesbordar_CuandoLaSumaConLoQueYaHayExcedeElRangoDeInt() {
        Carrito carritoExistente = Carrito.crear(USUARIO_ID);
        carritoExistente.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 100, true);
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoExistente));

        // 2 + Integer.MAX_VALUE desborda int a negativo: antes pasaba el chequeo y terminaba en 500
        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, Integer.MAX_VALUE))
                .isInstanceOf(StockInsuficienteException.class);

        assertThat(carritoExistente.cantidadDe(PRODUCTO_ID)).as("no se modifica el carrito").isEqualTo(2);
        verify(carritoRepositoryPort, org.mockito.Mockito.never()).guardar(any(Carrito.class));
    }

    @Test
    void execute_DeberiaRechazar_CuandoElProductoNoTieneStock() {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 0, true);
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 1))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 0 disponible");
    }

    @Test
    void execute_DeberiaRechazarConCeroDisponibles_CuandoElProductoEstaInactivoAunqueTengaStock() {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 50, false);
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agregarItemAlCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID, 1))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 0 disponible");
    }
}
