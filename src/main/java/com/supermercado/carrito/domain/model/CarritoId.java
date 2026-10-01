package com.supermercado.carrito.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class CarritoId {

    private final UUID valor;

    private CarritoId(UUID valor) {
        this.valor = Objects.requireNonNull(valor, "El id de carrito no puede ser nulo");
    }

    public static CarritoId nuevo() {
        return new CarritoId(UUID.randomUUID());
    }

    public static CarritoId de(UUID valor) {
        return new CarritoId(valor);
    }

    public static CarritoId de(String valor) {
        Objects.requireNonNull(valor, "El id de carrito no puede ser nulo");
        return new CarritoId(UUID.fromString(valor));
    }

    public UUID getValor() {
        return valor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CarritoId that = (CarritoId) o;
        return Objects.equals(valor, that.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
