package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.exception.CantidadMaximaExcedidaException;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.domain.exception.StockInsuficienteException;
import com.supermercado.carrito.domain.model.Carrito;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualizarCantidadItemUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;
    private final CatalogoProductosPort catalogoProductosPort;

    /**
     * @throws ItemNoEncontradoEnCarritoException si el usuario no tiene carrito, o el producto no esta en el.
     * @throws CantidadMaximaExcedidaException si la nueva cantidad supera Carrito.MAX_UNIDADES_POR_PRODUCTO.
     * @throws StockInsuficienteException si se SUBE la cantidad por encima del stock disponible.
     */
    @Transactional
    public Carrito execute(String usuarioId, UUID productoId, int nuevaCantidad) {
        if (nuevaCantidad > Carrito.MAX_UNIDADES_POR_PRODUCTO) {
            throw new CantidadMaximaExcedidaException(productoId, Carrito.MAX_UNIDADES_POR_PRODUCTO);
        }
        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .orElseThrow(() -> new ItemNoEncontradoEnCarritoException(
                        "El producto " + productoId + " no esta en el carrito"));

        // Solo si SUBE la cantidad se consulta el catalogo: bajarla nunca puede dejar sin stock, y asi un fallo del
        // catalogo no impide reducir un carrito. Si el item no esta en el carrito, actualizarCantidad lo reporta.
        int actual = carrito.cantidadDe(productoId);
        if (actual > 0 && nuevaCantidad > actual) {
            ProductoSnapshot producto = catalogoProductosPort.buscarProducto(productoId)
                    .orElseThrow(() -> new ProductoNoEncontradoException("Producto no encontrado: " + productoId));
            if (nuevaCantidad > producto.disponible()) {
                throw new StockInsuficienteException(productoId, producto.nombre(), producto.disponible());
            }
        }
        carrito.actualizarCantidad(productoId, nuevaCantidad);

        Carrito guardado = carritoRepositoryPort.guardar(carrito);
        log.info("Cantidad actualizada usuarioId={} productoId={} cantidad={}", usuarioId, productoId, nuevaCantidad);
        return guardado;
    }
}
