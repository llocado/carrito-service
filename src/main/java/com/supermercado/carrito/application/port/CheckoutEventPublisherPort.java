package com.supermercado.carrito.application.port;

import com.supermercado.carrito.domain.model.Carrito;
import java.util.UUID;

public interface CheckoutEventPublisherPort {

    /** Publica carrito.checkout-iniciado; checkoutId identifica este intento de checkout. */
    void publicarCheckoutIniciado(Carrito carrito, UUID checkoutId);
}
