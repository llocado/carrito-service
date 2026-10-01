package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaciarCarritoUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;

    /** Si el usuario no tiene carrito todavia, no hace nada -- ya esta "vacio". */
    @Transactional
    public void execute(String usuarioId) {
        carritoRepositoryPort.buscarPorUsuarioId(usuarioId).ifPresent(carrito -> {
            carrito.vaciar();
            carritoRepositoryPort.guardar(carrito);
        });
    }
}
