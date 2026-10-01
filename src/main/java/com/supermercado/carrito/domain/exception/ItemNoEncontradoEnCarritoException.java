package com.supermercado.carrito.domain.exception;

public class ItemNoEncontradoEnCarritoException extends RuntimeException {

    public ItemNoEncontradoEnCarritoException(String mensaje) {
        super(mensaje);
    }
}
