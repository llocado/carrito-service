package com.supermercado.carrito.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Linea de un carrito. Guarda una "foto" del producto (nombre, precio) tomada
 * en el momento en que se agrego al carrito -- carrito-service no importa el
 * modelo de dominio de productos-service (cada servicio es dueno del suyo),
 * asi que esta es su propia representacion minima, no el agregado completo.
 */
public final class ItemCarrito {

    private final UUID productoId;
    private final String nombre;
    private final BigDecimal precioUnitario;
    private final String moneda;
    private final int cantidad;

    private ItemCarrito(UUID productoId, String nombre, BigDecimal precioUnitario, String moneda, int cantidad) {
        this.productoId = Objects.requireNonNull(productoId, "El id de producto no puede ser nulo");
        this.nombre = Objects.requireNonNull(nombre, "El nombre no puede ser nulo");
        this.precioUnitario = Objects.requireNonNull(precioUnitario, "El precio unitario no puede ser nulo");
        this.moneda = Objects.requireNonNull(moneda, "La moneda no puede ser nula");
        validarCantidad(cantidad);
        this.cantidad = cantidad;
    }

    public static ItemCarrito de(UUID productoId, String nombre, BigDecimal precioUnitario, String moneda, int cantidad) {
        return new ItemCarrito(productoId, nombre, precioUnitario, moneda, cantidad);
    }

    public ItemCarrito conCantidad(int nuevaCantidad) {
        return new ItemCarrito(productoId, nombre, precioUnitario, moneda, nuevaCantidad);
    }

    public ItemCarrito incrementar(int unidades) {
        return conCantidad(this.cantidad + unidades);
    }

    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    private static void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
    }

    public UUID getProductoId() {
        return productoId;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public String getMoneda() {
        return moneda;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Igualdad por identidad de producto: en un carrito solo puede existir un item por producto. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemCarrito that = (ItemCarrito) o;
        return Objects.equals(productoId, that.productoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productoId);
    }

    @Override
    public String toString() {
        return "ItemCarrito{productoId=" + productoId + ", cantidad=" + cantidad + "}";
    }
}
