package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.domain.model.Carrito;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB
// (subclase en tiempo de ejecucion), y CGLIB no puede subclasificar una clase final.
@Slf4j
@Service
@RequiredArgsConstructor
public class AgregarItemAlCarritoUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;
    private final CatalogoProductosPort catalogoProductosPort;

    /**
     * @throws ProductoNoEncontradoException si productos-service no reconoce el producto.
     */
    @Transactional
    public Carrito execute(String usuarioId, UUID productoId, int cantidad) {
        ProductoSnapshot producto = catalogoProductosPort.buscarProducto(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + productoId));

        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .orElseGet(() -> Carrito.crear(usuarioId));

        carrito.agregarItem(producto.productoId(), producto.nombre(), producto.precioMonto(), producto.moneda(), cantidad);

        Carrito guardado = carritoRepositoryPort.guardar(carrito);
        log.info("Item agregado usuarioId={} productoId={} cantidad={}", usuarioId, productoId, cantidad);
        return guardado;
    }
}
