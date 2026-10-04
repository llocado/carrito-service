package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
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

    /**
     * @throws ItemNoEncontradoEnCarritoException si el usuario no tiene carrito, o el producto no esta en el.
     */
    @Transactional
    public Carrito execute(String usuarioId, UUID productoId, int nuevaCantidad) {
        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .orElseThrow(() -> new ItemNoEncontradoEnCarritoException(
                        "El producto " + productoId + " no esta en el carrito"));

        carrito.actualizarCantidad(productoId, nuevaCantidad);

        Carrito guardado = carritoRepositoryPort.guardar(carrito);
        log.info("Cantidad actualizada usuarioId={} productoId={} cantidad={}", usuarioId, productoId, nuevaCantidad);
        return guardado;
    }
}
