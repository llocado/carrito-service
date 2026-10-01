package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB.
@Service
@RequiredArgsConstructor
public class ObtenerTotalCarritoUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;

    /**
     * Si el usuario todavia no tiene carrito, el total es cero (sin persistir
     * nada). Necesita transaccion porque calcular el total recorre la
     * coleccion lazy de items.
     */
    @Transactional(readOnly = true)
    public BigDecimal execute(String usuarioId) {
        return carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .map(Carrito::calcularTotal)
                .orElse(BigDecimal.ZERO);
    }
}
