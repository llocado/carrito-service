package com.supermercado.carrito.application.port;

import com.supermercado.carrito.domain.model.Carrito;
import java.util.Optional;

public interface CarritoRepositoryPort {

    Carrito guardar(Carrito carrito);

    Optional<Carrito> buscarPorUsuarioId(String usuarioId);
}
