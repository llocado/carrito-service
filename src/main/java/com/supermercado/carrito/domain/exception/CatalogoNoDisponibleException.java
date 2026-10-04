package com.supermercado.carrito.domain.exception;

public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
