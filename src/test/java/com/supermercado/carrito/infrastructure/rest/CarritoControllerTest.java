package com.supermercado.carrito.infrastructure.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermercado.carrito.application.usecase.ActualizarCantidadItemUseCase;
import com.supermercado.carrito.application.usecase.AgregarItemAlCarritoUseCase;
import com.supermercado.carrito.application.usecase.ObtenerCarritoUseCase;
import com.supermercado.carrito.application.usecase.ObtenerTotalCarritoUseCase;
import com.supermercado.carrito.application.usecase.QuitarItemDelCarritoUseCase;
import com.supermercado.carrito.application.usecase.VaciarCarritoUseCase;
import com.supermercado.carrito.domain.exception.ItemNoEncontradoEnCarritoException;
import com.supermercado.carrito.domain.exception.ProductoNoEncontradoException;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.rest.dto.ActualizarCantidadRequest;
import com.supermercado.carrito.infrastructure.rest.dto.AgregarItemRequest;
import com.supermercado.carrito.infrastructure.rest.dto.CarritoDtoMapper;
import com.supermercado.carrito.infrastructure.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Slice de presentacion. Import explicito de SecurityConfig porque @WebMvcTest
 * no escanea @Configuration por defecto. JwtDecoder se mockea para que el
 * contexto no intente contactar a Keycloak al arrancar (issuer-uri real);
 * la autenticacion en si se simula con el post-processor jwt() de
 * spring-security-test, que pre-llena el SecurityContext sin pasar por el
 * decoder real.
 */
@WebMvcTest(CarritoController.class)
@Import({CarritoDtoMapper.class, SecurityConfig.class})
class CarritoControllerTest {

    private static final String USUARIO_ID = "usuario-123";
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ObtenerCarritoUseCase obtenerCarritoUseCase;

    @MockitoBean
    private ObtenerTotalCarritoUseCase obtenerTotalCarritoUseCase;

    @MockitoBean
    private AgregarItemAlCarritoUseCase agregarItemAlCarritoUseCase;

    @MockitoBean
    private ActualizarCantidadItemUseCase actualizarCantidadItemUseCase;

    @MockitoBean
    private QuitarItemDelCarritoUseCase quitarItemDelCarritoUseCase;

    @MockitoBean
    private VaciarCarritoUseCase vaciarCarritoUseCase;

    @Test
    void verCarrito_DeberiaRetornar200YElCarrito_CuandoElUsuarioEstaAutenticado() throws Exception {
        Carrito carrito = crearCarritoConUnItem();
        when(obtenerCarritoUseCase.execute(USUARIO_ID)).thenReturn(carrito);

        mockMvc.perform(get("/api/carrito").with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(USUARIO_ID))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void verCarrito_DeberiaRetornar401_CuandoNoHayToken() throws Exception {
        mockMvc.perform(get("/api/carrito"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verCarrito_DeberiaRetornar403_CuandoElRolNoEsCliente() throws Exception {
        mockMvc.perform(get("/api/carrito").with(jwt().jwt(j -> j.subject(USUARIO_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verTotal_DeberiaRetornar200YElTotal_CuandoElUsuarioEstaAutenticado() throws Exception {
        when(obtenerTotalCarritoUseCase.execute(USUARIO_ID)).thenReturn(new BigDecimal("3000"));

        mockMvc.perform(get("/api/carrito/total").with(jwtCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void verTotal_DeberiaRetornar401_CuandoNoHayToken() throws Exception {
        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verTotal_DeberiaRetornar403_CuandoElRolNoEsCliente() throws Exception {
        mockMvc.perform(get("/api/carrito/total").with(jwt().jwt(j -> j.subject(USUARIO_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void agregarItem_DeberiaRetornar200YElCarritoActualizado_CuandoElRequestEsValido() throws Exception {
        AgregarItemRequest request = new AgregarItemRequest(PRODUCTO_ID, 2);
        Carrito carrito = crearCarritoConUnItem();
        when(agregarItemAlCarritoUseCase.execute(eq(USUARIO_ID), eq(PRODUCTO_ID), eq(2))).thenReturn(carrito);

        mockMvc.perform(post("/api/carrito/items")
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productoId").value(PRODUCTO_ID.toString()));

        verify(agregarItemAlCarritoUseCase).execute(USUARIO_ID, PRODUCTO_ID, 2);
    }

    @Test
    void agregarItem_DeberiaRetornar400_CuandoLaCantidadEsInvalida() throws Exception {
        AgregarItemRequest requestInvalido = new AgregarItemRequest(PRODUCTO_ID, 0);

        mockMvc.perform(post("/api/carrito/items")
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());

        verify(agregarItemAlCarritoUseCase, never()).execute(any(), any(), anyInt());
    }

    @Test
    void agregarItem_DeberiaRetornar404_CuandoProductosServiceNoConoceElProducto() throws Exception {
        AgregarItemRequest request = new AgregarItemRequest(PRODUCTO_ID, 1);
        when(agregarItemAlCarritoUseCase.execute(eq(USUARIO_ID), eq(PRODUCTO_ID), eq(1)))
                .thenThrow(new ProductoNoEncontradoException("Producto no encontrado: " + PRODUCTO_ID));

        mockMvc.perform(post("/api/carrito/items")
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Producto no encontrado: " + PRODUCTO_ID));
    }

    @Test
    void actualizarCantidad_DeberiaRetornar200_CuandoElItemExiste() throws Exception {
        ActualizarCantidadRequest request = new ActualizarCantidadRequest(5);
        when(actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 5)).thenReturn(Carrito.crear(USUARIO_ID));

        mockMvc.perform(put("/api/carrito/items/{productoId}", PRODUCTO_ID)
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarCantidad_DeberiaRetornar404_CuandoElItemNoExiste() throws Exception {
        ActualizarCantidadRequest request = new ActualizarCantidadRequest(5);
        when(actualizarCantidadItemUseCase.execute(USUARIO_ID, PRODUCTO_ID, 5))
                .thenThrow(new ItemNoEncontradoEnCarritoException("El producto " + PRODUCTO_ID + " no esta en el carrito"));

        mockMvc.perform(put("/api/carrito/items/{productoId}", PRODUCTO_ID)
                        .with(jwtCliente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void quitarItem_DeberiaRetornar200_CuandoElItemExiste() throws Exception {
        when(quitarItemDelCarritoUseCase.execute(USUARIO_ID, PRODUCTO_ID)).thenReturn(Carrito.crear(USUARIO_ID));

        mockMvc.perform(delete("/api/carrito/items/{productoId}", PRODUCTO_ID).with(jwtCliente()))
                .andExpect(status().isOk());
    }

    @Test
    void vaciarCarrito_DeberiaRetornar204() throws Exception {
        mockMvc.perform(delete("/api/carrito").with(jwtCliente()))
                .andExpect(status().isNoContent());

        verify(vaciarCarritoUseCase).execute(USUARIO_ID);
    }

    private static JwtRequestPostProcessor jwtCliente() {
        return jwt().jwt(j -> j.subject(USUARIO_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_cliente"));
    }

    private static Carrito crearCarritoConUnItem() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(PRODUCTO_ID, "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        return carrito;
    }
}
