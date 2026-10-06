package com.supermercado.carrito.domain.model;

import java.util.UUID;

/** Producto y cantidad que el usuario efectivamente pago: lo que se descuenta del carrito. */
public record ItemComprado(UUID productoId, int cantidad) {
}
