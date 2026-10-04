package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CheckoutEventPublisherPort;
import com.supermercado.carrito.domain.exception.CarritoVacioException;
import com.supermercado.carrito.domain.model.Carrito;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IniciarCheckoutUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    @Mock
    private CheckoutEventPublisherPort checkoutEventPublisherPort;

    private IniciarCheckoutUseCase iniciarCheckoutUseCase;

    @BeforeEach
    void setUp() {
        iniciarCheckoutUseCase = new IniciarCheckoutUseCase(carritoRepositoryPort, checkoutEventPublisherPort);
    }

    @Test
    void execute_DeberiaPublicarElEventoConElCarritoYRetornarElCheckoutId_CuandoElCarritoTieneItems() {
        Carrito carrito = carritoConUnItem();
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        UUID checkoutId = iniciarCheckoutUseCase.execute(USUARIO_ID, null);

        verify(checkoutEventPublisherPort).publicarCheckoutIniciado(carrito, checkoutId);
        assertThat(checkoutId).isNotNull();
    }

    @Test
    void execute_NoDeberiaModificarNiGuardarElCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoConUnItem()));

        iniciarCheckoutUseCase.execute(USUARIO_ID, null);

        verify(carritoRepositoryPort, never()).guardar(any());
    }

    @Test
    void execute_DeberiaLanzarCarritoVacio_YNoPublicar_CuandoElCarritoNoTieneItems() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(Carrito.crear(USUARIO_ID)));

        assertThatThrownBy(() -> iniciarCheckoutUseCase.execute(USUARIO_ID, null))
                .isInstanceOf(CarritoVacioException.class);

        verify(checkoutEventPublisherPort, never()).publicarCheckoutIniciado(any(), any());
    }

    @Test
    void execute_DeberiaLanzarCarritoVacio_YNoPublicar_CuandoElUsuarioNoTieneCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iniciarCheckoutUseCase.execute(USUARIO_ID, null))
                .isInstanceOf(CarritoVacioException.class);

        verify(checkoutEventPublisherPort, never()).publicarCheckoutIniciado(any(), any());
    }

    @Test
    void execute_DeberiaGenerarUnCheckoutIdDistintoEnCadaLlamada_CuandoNoHayClaveDeIdempotencia() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoConUnItem()));

        UUID primero = iniciarCheckoutUseCase.execute(USUARIO_ID, null);
        UUID segundo = iniciarCheckoutUseCase.execute(USUARIO_ID, null);

        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void execute_DeberiaRetornarElMismoCheckoutId_CuandoSeReintentaConLaMismaClave() {
        UUID clave = UUID.randomUUID();
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoConUnItem()));

        UUID primero = iniciarCheckoutUseCase.execute(USUARIO_ID, clave);
        UUID segundo = iniciarCheckoutUseCase.execute(USUARIO_ID, clave);

        assertThat(primero).isEqualTo(segundo);
    }

    @Test
    void execute_DeberiaRetornarCheckoutIdDistinto_CuandoOtroUsuarioUsaLaMismaClave() {
        UUID clave = UUID.randomUUID();
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carritoConUnItem()));
        when(carritoRepositoryPort.buscarPorUsuarioId("otro-usuario")).thenReturn(Optional.of(carritoConUnItem()));

        UUID delUsuario = iniciarCheckoutUseCase.execute(USUARIO_ID, clave);
        UUID delOtro = iniciarCheckoutUseCase.execute("otro-usuario", clave);

        assertThat(delUsuario).isNotEqualTo(delOtro);
    }

    @Test
    void execute_DeberiaPublicarElCheckoutIdRetornado() {
        UUID clave = UUID.randomUUID();
        Carrito carrito = carritoConUnItem();
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));
        ArgumentCaptor<UUID> publicado = ArgumentCaptor.forClass(UUID.class);

        UUID retornado = iniciarCheckoutUseCase.execute(USUARIO_ID, clave);

        verify(checkoutEventPublisherPort).publicarCheckoutIniciado(any(), publicado.capture());
        assertThat(publicado.getValue()).isEqualTo(retornado);
    }

    private static Carrito carritoConUnItem() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(UUID.randomUUID(), "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        return carrito;
    }
}
