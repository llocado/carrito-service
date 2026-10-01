package com.supermercado.carrito.domain.exception;

/**
 * Se lanza cuando productos-service no reconoce el producto que se intenta
 * agregar al carrito. Excepcion propia de carrito-service -- no se importa la
 * de productos-service, cada servicio es dueno de su propio dominio.
 */
public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
