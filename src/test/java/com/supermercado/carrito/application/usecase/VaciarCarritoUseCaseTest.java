package com.supermercado.carrito.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
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
class VaciarCarritoUseCaseTest {

    private static final String USUARIO_ID = "usuario-123";

    @Mock
    private CarritoRepositoryPort carritoRepositoryPort;

    private VaciarCarritoUseCase vaciarCarritoUseCase;

    @BeforeEach
    void setUp() {
        vaciarCarritoUseCase = new VaciarCarritoUseCase(carritoRepositoryPort);
    }

    @Test
    void execute_DeberiaVaciarYGuardarElCarrito_CuandoExiste() {
        Carrito carrito = Carrito.crear(USUARIO_ID);
        carrito.agregarItem(UUID.randomUUID(), "Manzana Fuji", new BigDecimal("1500"), "CLP", 2);
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.of(carrito));

        vaciarCarritoUseCase.execute(USUARIO_ID);

        ArgumentCaptor<Carrito> captor = ArgumentCaptor.forClass(Carrito.class);
        verify(carritoRepositoryPort).guardar(captor.capture());
        assertThat(captor.getValue().getItems()).isEmpty();
    }

    @Test
    void execute_NoDeberiaHacerNada_CuandoElUsuarioNoTieneCarrito() {
        when(carritoRepositoryPort.buscarPorUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        vaciarCarritoUseCase.execute(USUARIO_ID);

        verify(carritoRepositoryPort, never()).guardar(any(Carrito.class));
    }
}
