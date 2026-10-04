package com.supermercado.carrito.infrastructure.rest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.exception.CatalogoNoDisponibleException;
import com.supermercado.carrito.domain.exception.CatalogoRespuestaInesperadaException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProductosServiceClientTest {

    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final String URL = "http://productos.test/api/productos/" + PRODUCTO_ID;

    private MockRestServiceServer servidor;
    private ProductosServiceClient cliente;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://productos.test");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ProductosServiceClient(builder.build());

        logger = (Logger) LoggerFactory.getLogger(ProductosServiceClient.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void limpiar() {
        logger.detachAppender(logs);
    }

    @Test
    void deberiaRetornarElSnapshot_CuandoProductosServiceResponde200() {
        servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"id":"%s","sku":"S1","nombre":"Pan","descripcion":"d","precioMonto":1200,"moneda":"CLP",
                 "stock":5,"categoriaId":"c","activo":true}""".formatted(PRODUCTO_ID), MediaType.APPLICATION_JSON));

        Optional<ProductoSnapshot> resultado = cliente.buscarProducto(PRODUCTO_ID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().nombre()).isEqualTo("Pan");
    }

    @Test
    void deberiaRetornarVacio_CuandoProductosServiceResponde404() {
        servidor.expect(requestTo(URL)).andRespond(withResourceNotFound());

        assertThat(cliente.buscarProducto(PRODUCTO_ID)).isEmpty();
        assertThat(logs.list).isEmpty();
    }

    @Test
    void deberiaLanzarCatalogoNoDisponible_YRegistrarSoloElEstado_CuandoProductosServiceResponde500() {
        servidor.expect(requestTo(URL)).andRespond(withServerError().body("traza interna: password=secreto-interno"));

        assertThatThrownBy(() -> cliente.buscarProducto(PRODUCTO_ID)).isInstanceOf(CatalogoNoDisponibleException.class);

        assertThat(logs.list).hasSize(1);
        String linea = logs.list.get(0).getFormattedMessage();
        assertThat(linea).contains("productoId=" + PRODUCTO_ID).contains("HTTP 500");
        assertThat(linea).doesNotContain("secreto-interno");
    }

    @Test
    void deberiaLanzarCatalogoNoDisponible_CuandoNoHayConexion() {
        servidor.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("Connection refused");
        });

        assertThatThrownBy(() -> cliente.buscarProducto(PRODUCTO_ID)).isInstanceOf(CatalogoNoDisponibleException.class);
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getFormattedMessage()).contains("productoId=" + PRODUCTO_ID);
    }

    @Test
    void deberiaLanzarRespuestaInesperada_NoCatalogoNoDisponible_CuandoProductosServiceRechazaConUn4xxDistintoDe404() {
        for (HttpStatus rechazo : new HttpStatus[] {HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            servidor.reset();
            logs.list.clear();
            servidor.expect(requestTo(URL)).andRespond(withStatus(rechazo));

            assertThatThrownBy(() -> cliente.buscarProducto(PRODUCTO_ID))
                    .isInstanceOf(CatalogoRespuestaInesperadaException.class)
                    .isNotInstanceOf(CatalogoNoDisponibleException.class);

            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.get(0).getFormattedMessage()).contains("productoId=" + PRODUCTO_ID).contains("HTTP " + rechazo.value());
        }
    }
}
