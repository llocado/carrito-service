package com.supermercado.carrito.infrastructure.rest;

import com.supermercado.carrito.domain.exception.CantidadMaximaExcedidaException;
import com.supermercado.carrito.domain.exception.CarritoVacioException;
import com.supermercado.carrito.domain.exception.CatalogoNoDisponibleException;
import com.supermercado.carrito.domain.exception.CatalogoRespuestaInesperadaException;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.domain.exception.StockInsuficienteException;
import com.supermercado.carrito.infrastructure.rest.dto.ErrorResponse;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.AmqpTimeoutException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Unicas que pierde una peticion que compite con otra identica: el INSERT del item (o del carrito, si es el
     * primero del usuario) choca antes de que el bloqueo optimista llegue a comparar versiones.
     */
    private static final Set<String> CONSTRAINTS_DE_CARRERA = Set.of("uk_items_carrito_carrito_producto", "uk_carritos_usuario_id");

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

    /** 422 como en productos-service. El mensaje solo lleva el nombre del producto y las unidades disponibles. */
    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handleStockInsuficiente(StockInsuficienteException ex) {
        log.info("Cantidad rechazada por stock insuficiente productoId={} disponible={}", ex.getProductoId(), ex.getDisponible());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage()));
    }

    /** 422 como el stock: la peticion es valida pero la regla de negocio la rechaza. El mensaje solo lleva el maximo. */
    @ExceptionHandler(CantidadMaximaExcedidaException.class)
    public ResponseEntity<ErrorResponse> handleCantidadMaximaExcedida(CantidadMaximaExcedidaException ex) {
        log.info("Cantidad rechazada por superar el maximo productoId={} maximo={}", ex.getProductoId(), ex.getMaximo());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage()));
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

    /**
     * Broker caido o inalcanzable (conexion rechazada, error de E/S o timeout): el detalle va al log, no a
     * la respuesta. Otros AmqpException (por ejemplo un error de conversion) son bugs, no cortes: se dejan
     * llegar como 500 en vez de decirle al cliente que reintente.
     */
    @ExceptionHandler({AmqpConnectException.class, AmqpIOException.class, AmqpTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleBrokerNoDisponible(RuntimeException ex) {
        log.error("No se pudo publicar en RabbitMQ", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("No se pudo iniciar el checkout en este momento. Intenta de nuevo."));
    }

    /** El detalle ya lo registro ProductosServiceClient; al cliente solo un mensaje fijo. */
    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleCatalogoNoDisponible(CatalogoNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }

    /** El detalle (estado HTTP) ya lo registro ProductosServiceClient; 502 porque el fallo esta en el servicio de abajo. */
    @ExceptionHandler(CatalogoRespuestaInesperadaException.class)
    public ResponseEntity<ErrorResponse> handleCatalogoRespuestaInesperada(CatalogoRespuestaInesperadaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Otro proceso (por ejemplo, el descuento por un pago aprobado) modifico el carrito mientras se procesaba esta
     * peticion: ninguno de los dos cambios se pierde en silencio y el cliente puede reintentar con el estado nuevo.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflictoDeConcurrencia(OptimisticLockingFailureException ex) {
        log.info("Conflicto de concurrencia al guardar el carrito: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("El carrito cambio mientras se procesaba tu peticion. Intenta de nuevo."));
    }

    /**
     * Dos peticiones simultaneas crearon lo mismo (mismo producto en el carrito, o el primer carrito del usuario): la que
     * pierde recibe 409 para reintentar, igual que en el conflicto de version. Cualquier otra violacion de integridad
     * es un defecto: se relanza para que quede como 500 con su traza.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleCarreraDeCreacion(DataIntegrityViolationException ex) {
        if (!(ex.getCause() instanceof ConstraintViolationException violacion)
                || !CONSTRAINTS_DE_CARRERA.contains(violacion.getConstraintName())) {
            throw ex;
        }
        log.info("Conflicto de concurrencia al crear en el carrito: constraint={}", violacion.getConstraintName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("El carrito cambio mientras se procesaba tu peticion. Intenta de nuevo."));
    }
}
