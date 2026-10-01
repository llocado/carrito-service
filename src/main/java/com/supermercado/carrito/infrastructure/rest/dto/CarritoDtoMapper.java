package com.supermercado.carrito.infrastructure.rest.dto;

import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.domain.model.ItemCarrito;
import org.springframework.stereotype.Component;

@Component
public class CarritoDtoMapper {

    public CarritoResponse toResponse(Carrito carrito) {
        return new CarritoResponse(
                carrito.getId().getValor().toString(),
                carrito.getUsuarioId(),
                carrito.getItems().stream().map(this::toResponse).toList(),
                carrito.calcularTotal()
        );
    }

    private ItemCarritoResponse toResponse(ItemCarrito item) {
        return new ItemCarritoResponse(
                item.getProductoId().toString(),
                item.getNombre(),
                item.getPrecioUnitario(),
                item.getMoneda(),
                item.getCantidad(),
                item.getSubtotal()
        );
    }
}
