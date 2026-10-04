package com.supermercado.carrito.domain.exception;

/** productos-service respondio con un 4xx distinto de 404 (por ejemplo 400, 401 o 403): no es una caida, es una configuracion o un contrato roto. */
public class CatalogoRespuestaInesperadaException extends RuntimeException {

    public CatalogoRespuestaInesperadaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
