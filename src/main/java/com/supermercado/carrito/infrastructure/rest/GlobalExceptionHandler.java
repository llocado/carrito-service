package com.supermercado.carrito.infrastructure.rest;

import com.supermercado.carrito.domain.exception.CarritoVacioException;
import com.supermercado.carrito.domain.exception.CatalogoNoDisponibleException;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.infrastructure.rest.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleProductoNoEncontrado(ProductoNoEncontradoException ex) {
        log.info("Producto no encontrado en el catalogo: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ItemNoEncontradoEnCarritoException.class)
    public ResponseEntity<ErrorResponse> handleItemNoEncontrado(ItemNoEncontradoEnCarritoException ex) {
        log.info("Item no encontrado en el carrito: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CarritoVacioException.class)
    public ResponseEntity<ErrorResponse> handleCarritoVacio(CarritoVacioException ex) {
        log.info("Checkout rechazado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    /** No se hace eco del valor recibido, solo del nombre del parametro. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParametroInvalido(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametro invalido en la peticion: {}", ex.getName());
        return ResponseEntity.badRequest().body(new ErrorResponse("El valor del parametro '" + ex.getName() + "' no es valido"));
    }

    /** Broker caido o inalcanzable: el detalle va al log, no a la respuesta. */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleBrokerNoDisponible(AmqpException ex) {
        log.error("No se pudo publicar en RabbitMQ", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("No se pudo iniciar el checkout en este momento. Intenta de nuevo."));
    }

    /** El detalle ya lo registro ProductosServiceClient; al cliente solo un mensaje fijo. */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleCatalogoNoDisponible(CatalogoNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }
}
