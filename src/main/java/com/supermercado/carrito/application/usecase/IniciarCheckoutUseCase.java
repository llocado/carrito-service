package com.supermercado.carrito.application.usecase;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CheckoutEventPublisherPort;
import com.supermercado.carrito.domain.exception.CarritoVacioException;
import com.supermercado.carrito.domain.model.Carrito;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No es "final": @Transactional necesita que Spring genere un proxy CGLIB.
@Service
@RequiredArgsConstructor
public class IniciarCheckoutUseCase {

    private final CarritoRepositoryPort carritoRepositoryPort;
    private final CheckoutEventPublisherPort checkoutEventPublisherPort;

    /**
     * Publica carrito.checkout-iniciado y retorna el id del intento. No
     * modifica el carrito: se vacia recien cuando el pago se aprueba (politica
     * decidida en el ROADMAP). Solo-lectura, pero necesita transaccion porque
     * leer los items recorre una coleccion lazy.
     *
     * Si el cliente manda una clave de idempotencia, el id del intento sale de
     * (usuario + clave): un reintento con la misma clave produce el mismo
     * eventId y pedidos-service lo descarta como duplicado. Mezclar el usuario
     * evita que alguien "pise" la clave de otro y haga descartar su checkout.
     */
    @Transactional(readOnly = true)
    public UUID execute(String usuarioId, UUID claveIdempotencia) {
        Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new CarritoVacioException("El carrito esta vacio: no hay nada que comprar"));

        UUID checkoutId = derivarCheckoutId(usuarioId, claveIdempotencia);
        checkoutEventPublisherPort.publicarCheckoutIniciado(carrito, checkoutId);
        return checkoutId;
    }

    private static UUID derivarCheckoutId(String usuarioId, UUID claveIdempotencia) {
        if (claveIdempotencia == null) {
            return UUID.randomUUID();
        }
        return UUID.nameUUIDFromBytes((usuarioId + ":" + claveIdempotencia).getBytes(StandardCharsets.UTF_8));
    }
}
