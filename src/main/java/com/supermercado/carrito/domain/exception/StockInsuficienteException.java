package com.supermercado.carrito.domain.exception;

import java.util.UUID;

/**
 * El cliente pide mas unidades de las que hay (o el producto esta inactivo, que cuenta como cero disponibles). Se
 * detecta al agregar o subir la cantidad para avisar temprano; la garantia final es el descuento de stock al pagar
 * (app-productos), porque entre agregar y pagar el stock puede cambiar.
 */
public class StockInsuficienteException extends RuntimeException {

    private final UUID productoId;
    private final int disponible;

    public StockInsuficienteException(UUID productoId, String nombre, int disponible) {
        super("Stock insuficiente de '" + nombre + "': hay " + disponible + " disponible(s)");
        this.productoId = productoId;
        this.disponible = disponible;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public int getDisponible() {
        return disponible;
    }
}
