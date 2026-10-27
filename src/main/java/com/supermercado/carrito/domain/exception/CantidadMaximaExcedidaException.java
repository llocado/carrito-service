package com.supermercado.carrito.domain.exception;

import java.util.UUID;

/**
 * El carrito llegaria a tener mas unidades de un mismo producto que el maximo permitido (Carrito.MAX_UNIDADES_POR_PRODUCTO).
 * Es una regla de negocio, distinta de la falta de stock: se comprueba primero y no necesita consultar el catalogo.
 */
public class CantidadMaximaExcedidaException extends RuntimeException {

    private final UUID productoId;
    private final int maximo;

    public CantidadMaximaExcedidaException(UUID productoId, int maximo) {
        super("No puedes tener mas de " + maximo + " unidades de un mismo producto en el carrito");
        this.productoId = productoId;
        this.maximo = maximo;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public int getMaximo() {
        return maximo;
    }
}
