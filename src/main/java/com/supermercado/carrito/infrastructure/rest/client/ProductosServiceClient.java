package com.supermercado.carrito.infrastructure.rest.client;

import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.domain.exception.CatalogoNoDisponibleException;
import com.supermercado.carrito.domain.exception.CatalogoRespuestaInesperadaException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador de salida: implementa el puerto CatalogoProductosPort llamando
 * por HTTP a productos-service. Es el unico punto de carrito-service que
 * conoce la forma de la respuesta REST de ese otro servicio.
 */
@Slf4j
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
        } catch (HttpClientErrorException e) {
            // 4xx distinto de 404 (ya capturado arriba): no es una caida, es un rechazo que reintentar no arregla.
            log.error("productos-service rechazo la consulta productoId={} causa=HTTP {}", productoId, e.getStatusCode().value());
            throw new CatalogoRespuestaInesperadaException("El catalogo de productos respondio de forma inesperada", e);
        } catch (RestClientException e) {
            // Solo el estado HTTP: el cuerpo de error de otro servicio no se vuelca al log.
            String causa = e instanceof RestClientResponseException r
                    ? "HTTP " + r.getStatusCode().value()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("productos-service no disponible o con error productoId={} causa={}", productoId, causa);
            throw new CatalogoNoDisponibleException("El catalogo de productos no esta disponible en este momento", e);
        }
    }

    // Subconjunto de ProductoResponse de productos-service que nos interesa.
    private record ProductoResponse(String id, String sku, String nombre, String descripcion,
                                     BigDecimal precioMonto, String moneda, int stock,
                                     String categoriaId, boolean activo) {
    }
}
