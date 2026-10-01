package com.supermercado.carrito.infrastructure.rest.client;

import com.supermercado.carrito.application.port.CatalogoProductosPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Adaptador de salida: implementa el puerto CatalogoProductosPort llamando
 * por HTTP a productos-service. Es el unico punto de carrito-service que
 * conoce la forma de la respuesta REST de ese otro servicio.
 */
@Component
@RequiredArgsConstructor
public class ProductosServiceClient implements CatalogoProductosPort {

    private final RestClient productosServiceRestClient;

    @Override
    public Optional<ProductoSnapshot> buscarProducto(UUID productoId) {
        try {
            ProductoResponse response = productosServiceRestClient.get()
                    .uri("/api/productos/{id}", productoId)
                    .retrieve()
                    .body(ProductoResponse.class);

            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new ProductoSnapshot(productoId, response.nombre(), response.precioMonto(), response.moneda()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // Subconjunto de ProductoResponse de productos-service que nos interesa.
    private record ProductoResponse(String id, String sku, String nombre, String descripcion,
                                     BigDecimal precioMonto, String moneda, int stock,
                                     String categoriaId, boolean activo) {
    }
}
