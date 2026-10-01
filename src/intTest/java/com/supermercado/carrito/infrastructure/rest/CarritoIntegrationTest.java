package com.supermercado.carrito.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort.ProductoSnapshot;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.rest.dto.ActualizarCantidadRequest;
import com.supermercado.carrito.infrastructure.rest.dto.AgregarItemRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Suite de integracion end-to-end (HTTP -> caso de uso -> adaptador JPA)
 * contra un PostgreSQL real gestionado por Testcontainers.
 *
 * Dos dependencias externas se mockean a proposito, no porque el test no
 * las necesite, sino porque no son responsabilidad de esta suite:
 * - JwtDecoder: evita depender de un Keycloak real corriendo para validar
 *   firmas; la autenticacion se simula con el post-processor jwt().
 * - CatalogoProductosPort: evita depender de que productos-service este
 *   levantado; carrito-service no es responsable de probar ese servicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CarritoIntegrationTest {

    private static final String USUARIO_ID = "usuario-integ-123";
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CarritoRepositoryPort carritoRepositoryPort;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CatalogoProductosPort catalogoProductosPort;

    @Test
    void agregarItem_DeberiaRetornar200YPersistirEnPostgres_CuandoElProductoExiste() throws Exception {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP");
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));

        AgregarItemRequest request = new AgregarItemRequest(PRODUCTO_ID, 2);

        mockMvc.perform(post("/api/carrito/items")
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nombre").value("Manzana Fuji"))
                .andExpect(jsonPath("$.total").value(3000));

        Optional<Carrito> guardado = carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID);
        assertThat(guardado).isPresent();
        assertThat(guardado.get().getItems()).hasSize(1);
        assertThat(guardado.get().getItems().get(0).getCantidad()).isEqualTo(2);
    }

    @Test
    void agregarItem_DeberiaRetornar404_CuandoProductosServiceNoConoceElProducto() throws Exception {
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.empty());

        AgregarItemRequest request = new AgregarItemRequest(PRODUCTO_ID, 1);

        mockMvc.perform(post("/api/carrito/items")
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void verCarrito_DeberiaRetornar401_CuandoNoHayToken() throws Exception {
        mockMvc.perform(get("/api/carrito"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verCarrito_DeberiaRetornar200YElCarritoPersistido_CuandoExiste() throws Exception {
        carritoRepositoryPort.guardar(carritoConUnItem());

        mockMvc.perform(get("/api/carrito").with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productoId").value(PRODUCTO_ID.toString()))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void verTotal_DeberiaRetornarLaSumaDelCarritoPersistido() throws Exception {
        carritoRepositoryPort.guardar(carritoConUnItem());

        mockMvc.perform(get("/api/carrito/total").with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void verTotal_DeberiaRetornarCero_CuandoElUsuarioNoTieneCarrito() throws Exception {
        mockMvc.perform(get("/api/carrito/total").with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void verTotal_DeberiaRetornar401_CuandoNoHayToken() throws Exception {
        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarCantidadYQuitarItem_DeberianReflejarseEnPostgres() throws Exception {
        ProductoSnapshot producto = new ProductoSnapshot(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP");
        when(catalogoProductosPort.buscarProducto(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        carritoRepositoryPort.guardar(carritoConUnItem());

        mockMvc.perform(put("/api/carrito/items/{productoId}", PRODUCTO_ID)
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActualizarCantidadRequest(9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(9));

        Optional<Carrito> trasActualizar = carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID);
        assertThat(trasActualizar).isPresent();
        assertThat(trasActualizar.get().getItems().get(0).getCantidad()).isEqualTo(9);

        mockMvc.perform(delete("/api/carrito/items/{productoId}", PRODUCTO_ID).with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        Optional<Carrito> trasQuitar = carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID);
        assertThat(trasQuitar).isPresent();
        assertThat(trasQuitar.get().getItems()).isEmpty();
    }

    @Test
    void vaciarCarrito_DeberiaRetornar204YDejarloVacioEnPostgres() throws Exception {
        carritoRepositoryPort.guardar(carritoConUnItem());

        mockMvc.perform(delete("/api/carrito").with(jwtCliente()))
                .andExpect(status().isNoContent());

        Optional<Carrito> guardado = carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID);
        assertThat(guardado).isPresent();
        assertThat(guardado.get().getItems()).isEmpty();
    }

    private static Carrito carritoConUnItem() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        return carrito;
    }

    private static JwtRequestPostProcessor jwtCliente() {
        return jwt().jwt(j -> j.subject(USUARIO_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_cliente"));
    }
}
