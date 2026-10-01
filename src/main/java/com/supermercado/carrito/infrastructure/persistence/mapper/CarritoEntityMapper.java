package com.supermercado.carrito.infrastructure.persistence.mapper;

import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.CarritoId;
import com.supermercado.carrito.domain.model.ItemCarrito;
import com.supermercado.carrito.infrastructure.persistence.entity.CarritoEntity;
import com.supermercado.carrito.infrastructure.persistence.entity.ItemCarritoEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CarritoEntityMapper {

    /**
     * Vuelca el estado del agregado de dominio sobre una entidad JPA ya
     * gestionada (o recien creada), reconciliando la coleccion de items por
     * productoId en vez de vaciarla y rellenarla a ciegas. Un simple
     * clear()+add() puede hacer que Hibernate intente el INSERT del item
     * "nuevo" antes del DELETE del viejo dentro del mismo flush cuando ambos
     * comparten productoId (ej. al actualizar una cantidad), violando la
     * constraint unica (carrito_id, producto_id). Actualizando en el lugar
     * los items que siguen existiendo se evita ese choque por completo.
     */
    public void volcarEnEntidad(Carrito carrito, CarritoEntity entity) {
        entity.setId(carrito.getId().getValor());
        entity.setUsuarioId(carrito.getUsuarioId());
        entity.setCreadoEn(carrito.getCreadoEn());
        entity.setActualizadoEn(carrito.getActualizadoEn());

        Map<UUID, ItemCarritoEntity> existentesPorProductoId = new HashMap<>();
        for (ItemCarritoEntity itemEntity : entity.getItems()) {
            existentesPorProductoId.put(itemEntity.getProductoId(), itemEntity);
        }

        entity.getItems().removeIf(itemEntity -> carrito.getItems().stream()
                .noneMatch(item -> item.getProductoId().equals(itemEntity.getProductoId())));

        for (ItemCarrito item : carrito.getItems()) {
            ItemCarritoEntity itemEntity = existentesPorProductoId.get(item.getProductoId());
            if (itemEntity == null) {
                itemEntity = new ItemCarritoEntity();
                itemEntity.setCarrito(entity);
                itemEntity.setProductoId(item.getProductoId());
                entity.getItems().add(itemEntity);
            }
            itemEntity.setNombre(item.getNombre());
            itemEntity.setPrecioUnitario(item.getPrecioUnitario());
            itemEntity.setMoneda(item.getMoneda());
            itemEntity.setCantidad(item.getCantidad());
        }
    }

    public Carrito toDomain(CarritoEntity entity) {
        var items = entity.getItems().stream()
                .map(itemEntity -> ItemCarrito.de(
                        itemEntity.getProductoId(),
                        itemEntity.getNombre(),
                        itemEntity.getPrecioUnitario(),
                        itemEntity.getMoneda(),
                        itemEntity.getCantidad()))
                .toList();

        return Carrito.reconstruir(
                CarritoId.de(entity.getId()),
                entity.getUsuarioId(),
                items,
                entity.getCreadoEn(),
                entity.getActualizadoEn()
        );
    }
}
