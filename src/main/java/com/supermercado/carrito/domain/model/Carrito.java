package com.supermercado.carrito.domain.model;

import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Carrito {

    private final CarritoId id;
    private final String usuarioId;
    private final List<ItemCarrito> items;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    private Carrito(CarritoId id, String usuarioId, List<ItemCarrito> items, Instant creadoEn, Instant actualizadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.items = new ArrayList<>(items);
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    public static Carrito crear(String usuarioId) {
        validarUsuarioId(usuarioId);
        Instant ahora = Instant.now();
        return new Carrito(CarritoId.nuevo(), usuarioId, List.of(), ahora, ahora);
    }

    public static Carrito reconstruir(CarritoId id, String usuarioId, List<ItemCarrito> items,
                                       Instant creadoEn, Instant actualizadoEn) {
        validarUsuarioId(usuarioId);
        return new Carrito(
                Objects.requireNonNull(id, "El id no puede ser nulo"),
                usuarioId,
                Objects.requireNonNull(items, "Los items no pueden ser nulos"),
                Objects.requireNonNull(creadoEn, "creadoEn no puede ser nulo"),
                Objects.requireNonNull(actualizadoEn, "actualizadoEn no puede ser nulo")
        );
    }

    /** Agrega un producto nuevo, o incrementa la cantidad si ya estaba en el carrito. */
    public void agregarItem(UUID productoId, String nombre, BigDecimal precioUnitario, String moneda, int cantidad) {
        Optional<ItemCarrito> existente = buscarItem(productoId);
        if (existente.isPresent()) {
            reemplazarItem(existente.get().incrementar(cantidad));
        } else {
            items.add(ItemCarrito.de(productoId, nombre, precioUnitario, moneda, cantidad));
        }
        marcarActualizado();
    }

    public void actualizarCantidad(UUID productoId, int nuevaCantidad) {
        ItemCarrito item = buscarItem(productoId)
                .orElseThrow(() -> new ItemNoEncontradoEnCarritoException(
                        "El producto " + productoId + " no esta en el carrito"));
        reemplazarItem(item.conCantidad(nuevaCantidad));
        marcarActualizado();
    }

    public void quitarItem(UUID productoId) {
        boolean eliminado = items.removeIf(item -> item.getProductoId().equals(productoId));
        if (!eliminado) {
            throw new ItemNoEncontradoEnCarritoException("El producto " + productoId + " no esta en el carrito");
        }
        marcarActualizado();
    }

    public void vaciar() {
        items.clear();
        marcarActualizado();
    }

    public BigDecimal calcularTotal() {
        return items.stream()
                .map(ItemCarrito::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<ItemCarrito> buscarItem(UUID productoId) {
        return items.stream().filter(item -> item.getProductoId().equals(productoId)).findFirst();
    }

    private void reemplazarItem(ItemCarrito actualizado) {
        items.removeIf(item -> item.getProductoId().equals(actualizado.getProductoId()));
        items.add(actualizado);
    }

    private void marcarActualizado() {
        this.actualizadoEn = Instant.now();
    }

    private static void validarUsuarioId(String usuarioId) {
        Objects.requireNonNull(usuarioId, "El id de usuario no puede ser nulo");
        if (usuarioId.isBlank()) {
            throw new IllegalArgumentException("El id de usuario no puede estar vacio");
        }
    }

    public CarritoId getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public List<ItemCarrito> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
