package com.supermercado.carrito.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemCarritoTest {

    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Test
    void de_DeberiaCrearItem_CuandoLosDatosSonValidos() {
        ItemCarrito item = ItemCarrito.de(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        assertThat(item.getProductoId()).isEqualTo(PRODUCTO_ID);
        assertThat(item.getCantidad()).isEqualTo(2);
        assertThat(item.getSubtotal()).isEqualByComparingTo("3000");
    }

    @Test
    void de_DeberiaLanzarExcepcion_CuandoLaCantidadEsCeroONegativa() {
        assertThatThrownBy(() -> ItemCarrito.de(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemCarrito.de(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incrementar_DeberiaSumarLaCantidad_SinMutarElOriginal() {
        ItemCarrito original = ItemCarrito.de(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);

        ItemCarrito incrementado = original.incrementar(3);

        assertThat(original.getCantidad()).isEqualTo(2);
        assertThat(incrementado.getCantidad()).isEqualTo(5);
    }

    @Test
    void equals_DeberiaSerPorProductoId_IgnorandoElRestoDeLosCampos() {
        ItemCarrito item1 = ItemCarrito.de(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        ItemCarrito item2 = ItemCarrito.de(PRODUCTO_ID, "Otro nombre", new BigDecimal("999"), "USD", 10);

        assertThat(item1).isEqualTo(item2);
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
    }
}
