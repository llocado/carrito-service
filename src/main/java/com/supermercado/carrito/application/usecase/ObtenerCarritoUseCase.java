package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB
// (subclase en tiempo de ejecucion), y CGLIB no puede subclasificar una clase final.
@Service
@RequiredArgsConstructor
public class ObtenerCarritoUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;

    /**
     * Si el usuario todavia no tiene carrito, retorna uno vacio sin persistirlo.
     * Solo-lectura, pero igual necesita una transaccion activa: sin ella, el
     * mapper de persistencia no puede leer la coleccion de items (lazy) --
     * la sesion de Hibernate ya se cerro para cuando se intenta el mapeo.
     */
    @Transactional(readOnly = true)
    public Carrito execute(String usuarioId) {
        return carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .orElseGet(() -> Carrito.crear(usuarioId));
    }
}
