package com.supermercado.carrito.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarritoTest {

    private static final String USUARIO_ID = "usuario-123";
    private static final UUID PRODUCTO_MANZANA = UUID.randomUUID();
    private static final UUID PRODUCTO_PERA = UUID.randomUUID();

    @Test
    void crear_DeberiaCrearCarritoVacio() {
        Carrito carrito = Carrito.crear(USUARIO_ID);

        assertThat(carrito.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(carrito.getItems()).isEmpty();
        assertThat(carrito.calcularTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void crear_DeberiaLanzarExcepcion_CuandoUsuarioIdEsVacio() {
        assertThatThrownBy(() -> Carrito.crear(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agregarItem_DeberiaAgregarUnItemNuevo_CuandoElProductoNoEstabaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);

        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(2);
        assertThat(carrito.calcularTotal()).isEqualByComparingTo("3000");
    }

    @Test
    void agregarItem_DeberiaIncrementarLaCantidad_CuandoElProductoYaEstabaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 3);

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(5);
    }

    @Test
    void agregarItem_DeberiaMantenerItemsDistintosSeparados() {
        Carrito carrito = Carrito.crear(USUARIO_ID);

        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        carrito.agregarItem(PRODUCTO_PERA, "Pera Williams", new BigDecimal("1200"), "CLP", 1);

        assertThat(carrito.getItems()).hasSize(2);
        assertThat(carrito.calcularTotal()).isEqualByComparingTo("4200");
    }

    @Test
    void actualizarCantidad_DeberiaCambiarLaCantidadDelItem_CuandoExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        carrito.actualizarCantidad(PRODUCTO_MANZANA, 10);

        assertThat(carrito.getItems().get(0).getCantidad()).isEqualTo(10);
    }

    @Test
    void actualizarCantidad_DeberiaLanzarExcepcion_CuandoElProductoNoEstaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);

        assertThatThrownBy(() -> carrito.actualizarCantidad(PRODUCTO_MANZANA, 10))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);
    }

    @Test
    void quitarItem_DeberiaEliminarElItem_CuandoExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        carrito.agregarItem(PRODUCTO_PERA, "Pera Williams", new BigDecimal("1200"), "CLP", 1);

        carrito.quitarItem(PRODUCTO_MANZANA);

        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getProductoId()).isEqualTo(PRODUCTO_PERA);
    }

    @Test
    void quitarItem_DeberiaLanzarExcepcion_CuandoElProductoNoEstaEnElCarrito() {
        Carrito carrito = Carrito.crear(USUARIO_ID);

        assertThatThrownBy(() -> carrito.quitarItem(PRODUCTO_MANZANA))
                .isInstanceOf(ItemNoEncontradoEnCarritoException.class);
    }

    @Test
    void vaciar_DeberiaDejarElCarritoSinItems() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        carrito.vaciar();

        assertThat(carrito.getItems()).isEmpty();
        assertThat(carrito.calcularTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
