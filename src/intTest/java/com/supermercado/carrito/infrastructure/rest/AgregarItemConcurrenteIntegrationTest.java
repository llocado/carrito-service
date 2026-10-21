package com.supermercado.carrito.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.rest.dto.AgregarItemRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Peticiones HTTP simultaneas de verdad (hilos y transacciones reales, sin @Transactional en la clase) que agregan el
 * mismo producto. Antes del arreglo el INSERT del item chocaba con la unique (carrito_id, producto_id) antes de que el
 * bloqueo optimista pudiera actuar, y el cliente recibia 500 en vez de 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class AgregarItemConcurrenteIntegrationTest {

    private static final int HILOS = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CarritoRepositoryPort carritoRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CatalogoProductosPort catalogoProductosPort;

    @Test
    void agregarElMismoProductoEnParalelo_DeberiaDarSoloEstados200o409_YNoPerderNiDuplicarElItem_ConCarritoExistente() throws Exception {
        String usuarioId = "usuario-par-" + UUID.randomUUID();
        UUID otroProducto = UUID.randomUUID();
        UUID producto = UUID.randomUUID();
        prepararCatalogo(producto);
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            Carrito carrito = Carrito.crear(usuarioId);
            carrito.agregarItem(otroProducto, "Pera", new BigDecimal("900"), "CLP", 1);
            carritoRepositoryPort.guardar(carrito);
        });

        verificarCarrera(usuarioId, producto);
    }

    @Test
    void agregarElMismoProductoEnParalelo_DeberiaDarSoloEstados200o409_ConCarritoQueAunNoExiste() throws Exception {
        String usuarioId = "usuario-nuevo-" + UUID.randomUUID();
        UUID producto = UUID.randomUUID();
        prepararCatalogo(producto);

        verificarCarrera(usuarioId, producto);
    }

    private void prepararCatalogo(UUID producto) {
        when(catalogoProductosPort.buscarProducto(producto))
                .thenReturn(Optional.of(new ProductoSnapshot(producto, "Manzana Fuji", new BigDecimal("1500"), "CLP", 1000, true)));
    }

    private void verificarCarrera(String usuarioId, UUID producto) throws Exception {
        CyclicBarrier barrera = new CyclicBarrier(HILOS);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                futuros.add(pool.submit(() -> {
                    barrera.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/carrito/items")
                                    .with(jwt().jwt(j -> j.subject(usuarioId)).authorities(new SimpleGrantedAuthority("ROLE_cliente")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new AgregarItemRequest(producto, 1))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        long exitos = estados.stream().filter(e -> e == 200).count();
        assertThat(estados).as("nunca 500: lo que pierde la carrera es un 409 para reintentar").allMatch(e -> e == 200 || e == 409);
        assertThat(exitos).as("al menos una peticion gana").isGreaterThanOrEqualTo(1);

        Carrito guardado = new TransactionTemplate(transactionManager)
                .execute(estado -> carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow());
        assertThat(guardado.getItems()).filteredOn(i -> i.getProductoId().equals(producto)).singleElement()
                .satisfies(i -> assertThat(i.getCantidad()).as("una unidad por cada 200, ninguna perdida ni duplicada")
                        .isEqualTo((int) exitos));
    }
}
