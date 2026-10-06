package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.EventoProcesadoPort;
import com.supermercado.carrito.domain.model.ItemComprado;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB.
@Slf4j
@Service
@RequiredArgsConstructor
public class DescontarItemsCompradosUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;
    private final EventoProcesadoPort eventoProcesadoPort;

    /**
     * Cuando un pago se aprueba (pago.aprobado) se descuenta del carrito solo lo comprado, no se vacia completo.
     * No es naturalmente idempotente (restar dos veces la misma cantidad quita de mas), por eso se guarda el
     * eventId procesado: una reentrega del mismo evento no vuelve a descontar.
     */
    @Transactional
    public void execute(UUID eventId, String usuarioId, List<ItemComprado> comprados) {
        if (eventoProcesadoPort.yaFueProcesado(eventId)) {
            log.info("Evento ya procesado, se descarta el duplicado eventId={}", eventId);
            return;
        }
        carritoRepositoryPort.buscarPorUsuarioId(usuarioId).ifPresentOrElse(carrito -> {
            carrito.descontarComprado(comprados);
            carritoRepositoryPort.guardar(carrito);
            log.info("Items comprados descontados del carrito usuarioId={} items={} quedan={}",
                    usuarioId, comprados.size(), carrito.getItems().size());
        }, () -> log.info("El usuario no tiene carrito, no hay nada que descontar usuarioId={}", usuarioId));
        eventoProcesadoPort.marcarComoProcesado(eventId);
    }
}
