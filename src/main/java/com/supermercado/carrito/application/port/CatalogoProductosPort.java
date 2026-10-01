package com.supermercado.carrito.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida hacia productos-service. La implementacion concreta (un
 * cliente REST) vive en infrastructure; el caso de uso solo conoce este
 * contrato. Decision de diseno: se consulta de forma sincrona en el momento
 * de agregar al carrito, en vez de mantener un read-model propio alimentado
 * por eventos -- evita depender de RabbitMQ para esta operacion y evita el
 * problema de "productos invisibles" para carritos si el evento de creacion
 * nunca se publico (ej. productos creados antes de que existiera el publisher).
 */
public interface CatalogoProductosPort {

    Optional<ProductoSnapshot> buscarProducto(UUID productoId);

    record ProductoSnapshot(UUID productoId, String nombre, java.math.BigDecimal precioMonto, String moneda) {
    }
}
