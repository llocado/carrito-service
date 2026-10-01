package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.model.Carrito;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuitarItemDelCarritoUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;

    /**
     * @throws ItemNoEncontradoEnCarritoException si el usuario no tiene carrito, o el producto no esta en el.
     */
    @Transactional
    public Carrito execute(String usuarioId, UUID productoId) {
        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .orElseThrow(() -> new ItemNoEncontradoEnCarritoException(
                        "El producto " + productoId + " no esta en el carrito"));

        carrito.quitarItem(productoId);

        return carritoRepositoryPort.guardar(carrito);
    }
}
