package com.supermercado.carrito.domain.exception;

public class CarritoVacioException extends RuntimeException {

    public CarritoVacioException(String mensaje) {
        super(mensaje);
    }
}
