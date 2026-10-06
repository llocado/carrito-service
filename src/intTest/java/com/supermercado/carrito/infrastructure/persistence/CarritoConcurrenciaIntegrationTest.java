package com.supermercado.carrito.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.application.port.CatalogoProductosPort;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.ItemComprado;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dos transacciones reales sobre el mismo carrito (sin @Transactional en la clase: cada TransactionTemplate abre y
 * confirma la suya). Reproduce el escenario del pago aprobado que llega mientras el usuario edita su carrito: el
 * segundo en confirmar debe fallar en vez de pisar el cambio del primero.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class CarritoConcurrenciaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CarritoRepositoryPort carritoRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CatalogoProductosPort catalogoProductosPort;

    @Test
    void elSegundoEnConfirmarFalla_YNoPisaElCambioDelPrimero() {
        String usuarioId = "usuario-conc-" + UUID.randomUUID();
        UUID manzana = UUID.randomUUID();
        UUID pera = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(estado -> {
            Carrito carrito = Carrito.crear(usuarioId);
            carrito.agregarItem(manzana, "Manzana", new BigDecimal("1500"), "CLP", 2);
            carritoRepositoryPort.guardar(carrito);
        });

        // A (el descuento por el pago): lee el carrito con 2 manzanas...
        assertThatThrownBy(() -> tx.executeWithoutResult(estadoA -> {
            Carrito leidoPorA = carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow();

            // ...y mientras tanto B (el usuario por REST) agrega una pera y confirma.
            TransactionTemplate txB = new TransactionTemplate(transactionManager);
            txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            txB.executeWithoutResult(estadoB -> {
                Carrito leidoPorB = carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow();
                leidoPorB.agregarItem(pera, "Pera", new BigDecimal("900"), "CLP", 3);
                carritoRepositoryPort.guardar(leidoPorB);
            });

            // A calcula sobre lo que leyo (sin la pera) y trata de guardar: no debe pisar el cambio de B.
            leidoPorA.descontarComprado(List.of(new ItemComprado(manzana, 2)));
            carritoRepositoryPort.guardar(leidoPorA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        Carrito final_ = leer(usuarioId);
        assertThat(final_.getItems()).extracting(i -> i.getProductoId()).containsExactlyInAnyOrder(manzana, pera);
        assertThat(final_.getItems()).filteredOn(i -> i.getProductoId().equals(pera)).singleElement()
                .satisfies(i -> assertThat(i.getCantidad()).isEqualTo(3));
    }

    @Test
    void sinConcurrencia_LasEscriturasSucesivasFuncionanNormalmente() {
        String usuarioId = "usuario-sec-" + UUID.randomUUID();
        UUID manzana = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(estado -> {
            Carrito carrito = Carrito.crear(usuarioId);
            carrito.agregarItem(manzana, "Manzana", new BigDecimal("1500"), "CLP", 5);
            carritoRepositoryPort.guardar(carrito);
        });

        for (int i = 0; i < 3; i++) {
            tx.executeWithoutResult(estado -> {
                Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow();
                carrito.descontarComprado(List.of(new ItemComprado(manzana, 1)));
                carritoRepositoryPort.guardar(carrito);
            });
        }

        Carrito resultado = leer(usuarioId);
        assertThat(resultado.getItems().get(0).getCantidad()).isEqualTo(2);
    }

    @Test
    void dosEscritoresSimultaneosDeRealidad_UnoConfirmaYElOtroFalla_SinPerderElItemDelGanador() throws Exception {
        String usuarioId = "usuario-par-" + UUID.randomUUID();
        UUID manzana = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(estado -> {
            Carrito carrito = Carrito.crear(usuarioId);
            carrito.agregarItem(manzana, "Manzana", new BigDecimal("1500"), "CLP", 10);
            carritoRepositoryPort.guardar(carrito);
        });

        java.util.concurrent.CyclicBarrier barrera = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Boolean>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    try {
                        tx.executeWithoutResult(estado -> {
                            Carrito carrito = carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow();
                            esperar(barrera); // los dos ya leyeron la misma version
                            carrito.descontarComprado(List.of(new ItemComprado(manzana, 4)));
                            carritoRepositoryPort.guardar(carrito);
                        });
                        return true;
                    } catch (OptimisticLockingFailureException e) {
                        return false;
                    }
                }));
            }
            long confirmados = 0;
            for (var f : futuros) {
                if (f.get(30, java.util.concurrent.TimeUnit.SECONDS)) confirmados++;
            }
            assertThat(confirmados).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // Se descuenta una sola vez (10 - 4), no dos (que daria 2) ni se pierde una escritura.
        assertThat(leer(usuarioId).getItems().get(0).getCantidad()).isEqualTo(6);
    }

    /** Los items son lazy y open-in-view esta apagado: la lectura necesita su propia transaccion. */
    private Carrito leer(String usuarioId) {
        return new TransactionTemplate(transactionManager).execute(estado -> carritoRepositoryPort.buscarPorUsuarioId(usuarioId).orElseThrow());
    }

    private static void esperar(java.util.concurrent.CyclicBarrier barrera) {
        try {
            barrera.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
