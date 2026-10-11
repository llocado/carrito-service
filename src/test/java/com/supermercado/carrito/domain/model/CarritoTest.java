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

    private static Carrito carritoConManzanasYPeras(java.time.Instant ultimaActualizacion) {
        ItemCarrito manzanas = ItemCarrito.de(PRODUCTO_MANZANA, "Manzana Fuji", new BigDecimal("1500"), "CLP", 5);
        ItemCarrito peras = ItemCarrito.de(PRODUCTO_PERA, "Pera", new BigDecimal("1000"), "CLP", 2);
        return Carrito.reconstruir(CarritoId.nuevo(), USUARIO_ID, java.util.List.of(manzanas, peras), ultimaActualizacion, ultimaActualizacion);
    }

    @Test
    void descontarComprado_DeberiaRestarLaCantidadComprada_ConservandoLoQueSobra() {
        java.time.Instant antes = java.time.Instant.now().minusSeconds(3600);
        Carrito carrito = carritoConManzanasYPeras(antes);

        carrito.descontarComprado(java.util.List.of(new ItemComprado(PRODUCTO_MANZANA, 3)));

        assertThat(carrito.getItems()).hasSize(2);
        assertThat(carrito.getItems()).filteredOn(i -> i.getProductoId().equals(PRODUCTO_MANZANA))
                .singleElement().satisfies(i -> assertThat(i.getCantidad()).isEqualTo(2));
        assertThat(carrito.getActualizadoEn()).isAfter(antes);
    }

    @Test
    void descontarComprado_DeberiaEliminarElItem_CuandoSeCompraTodaLaCantidad() {
        Carrito carrito = carritoConManzanasYPeras(java.time.Instant.now());

        carrito.descontarComprado(java.util.List.of(new ItemComprado(PRODUCTO_PERA, 2)));

        assertThat(carrito.getItems()).extracting(ItemCarrito::getProductoId).containsExactly(PRODUCTO_MANZANA);
    }

    @Test
    void descontarComprado_DeberiaEliminarElItem_CuandoSeCompraMasDeLoQueHabia() {
        Carrito carrito = carritoConManzanasYPeras(java.time.Instant.now());

        carrito.descontarComprado(java.util.List.of(new ItemComprado(PRODUCTO_PERA, 10)));

        assertThat(carrito.getItems()).extracting(ItemCarrito::getProductoId).containsExactly(PRODUCTO_MANZANA);
    }

    @Test
    void descontarComprado_DeberiaDejarElCarritoVacio_CuandoSeCompraTodo() {
        Carrito carrito = carritoConManzanasYPeras(java.time.Instant.now());

        carrito.descontarComprado(java.util.List.of(new ItemComprado(PRODUCTO_MANZANA, 5), new ItemComprado(PRODUCTO_PERA, 2)));

        assertThat(carrito.getItems()).isEmpty();
    }

    @Test
    void descontarComprado_DeberiaIgnorarUnProductoQueYaNoEstaEnElCarrito_SinMarcarCambios() {
        java.time.Instant antes = java.time.Instant.now().minusSeconds(3600);
        Carrito carrito = carritoConManzanasYPeras(antes);

        carrito.descontarComprado(java.util.List.of(new ItemComprado(UUID.randomUUID(), 1)));

        assertThat(carrito.getItems()).hasSize(2);
        assertThat(carrito.getActualizadoEn()).as("sin cambios no se toca actualizadoEn").isEqualTo(antes);
    }

    @Test
    void descontarComprado_DeberiaSumarLasCantidades_CuandoElMismoProductoVieneRepetido() {
        Carrito carrito = carritoConManzanasYPeras(java.time.Instant.now());

        carrito.descontarComprado(java.util.List.of(new ItemComprado(PRODUCTO_MANZANA, 1), new ItemComprado(PRODUCTO_MANZANA, 2)));

        assertThat(carrito.getItems()).filteredOn(i -> i.getProductoId().equals(PRODUCTO_MANZANA))
                .singleElement().satisfies(i -> assertThat(i.getCantidad()).isEqualTo(2));
    }

    @Test
    void descontarComprado_DeberiaFallar_CuandoLaListaEsNula() {
        assertThatThrownBy(() -> Carrito.crear(USUARIO_ID).descontarComprado(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cantidadDe_DeberiaDevolverLasUnidadesDelProducto_OCeroSiNoEsta() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_MANZANA, "Manzana", new BigDecimal("1500"), "CLP", 3);

        assertThat(carrito.cantidadDe(PRODUCTO_MANZANA)).isEqualTo(3);
        assertThat(carrito.cantidadDe(UUID.randomUUID())).isZero();
    }
}
