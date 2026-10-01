package com.supermercado.carrito.infrastructure.rest;

import com.supermercado.carrito.application.usecase.ActualizarCantidadItemUseCase;
import com.supermercado.carrito.application.usecase.AgregarItemAlCarritoUseCase;
import com.supermercado.carrito.application.usecase.ObtenerCarritoUseCase;
import com.supermercado.carrito.application.usecase.ObtenerTotalCarritoUseCase;
import com.supermercado.carrito.application.usecase.QuitarItemDelCarritoUseCase;
import com.supermercado.carrito.application.usecase.VaciarCarritoUseCase;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.rest.dto.ActualizarCantidadRequest;
import com.supermercado.carrito.infrastructure.rest.dto.AgregarItemRequest;
import com.supermercado.carrito.infrastructure.rest.dto.CarritoDtoMapper;
import com.supermercado.carrito.infrastructure.rest.dto.CarritoResponse;
import com.supermercado.carrito.infrastructure.rest.dto.TotalCarritoResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP. El id de usuario nunca viene del cliente -- se
 * saca siempre del claim "sub" del JWT validado por Spring Security, para que
 * un usuario no pueda operar sobre el carrito de otro cambiando un parametro.
 */
@RestController
@RequestMapping("/api/carrito")
@RequiredArgsConstructor
public class CarritoController {

    private final ObtenerCarritoUseCase obtenerCarritoUseCase;
    private final ObtenerTotalCarritoUseCase obtenerTotalCarritoUseCase;
    private final AgregarItemAlCarritoUseCase agregarItemAlCarritoUseCase;
    private final ActualizarCantidadItemUseCase actualizarCantidadItemUseCase;
    private final QuitarItemDelCarritoUseCase quitarItemDelCarritoUseCase;
    private final VaciarCarritoUseCase vaciarCarritoUseCase;
    private final CarritoDtoMapper carritoDtoMapper;

    @GetMapping
    public ResponseEntity<CarritoResponse> verCarrito(@AuthenticationPrincipal Jwt jwt) {
        Carrito carrito = obtenerCarritoUseCase.execute(jwt.getSubject());
        return ResponseEntity.ok(carritoDtoMapper.toResponse(carrito));
    }

    @GetMapping("/total")
    public ResponseEntity<TotalCarritoResponse> verTotal(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new TotalCarritoResponse(obtenerTotalCarritoUseCase.execute(jwt.getSubject())));
    }

    @PostMapping("/items")
    public ResponseEntity<CarritoResponse> agregarItem(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgregarItemRequest request) {
        Carrito carrito = agregarItemAlCarritoUseCase.execute(jwt.getSubject(), request.productoId(), request.cantidad());
        return ResponseEntity.ok(carritoDtoMapper.toResponse(carrito));
    }

    @PutMapping("/items/{productoId}")
    public ResponseEntity<CarritoResponse> actualizarCantidad(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID productoId,
            @Valid @RequestBody ActualizarCantidadRequest request) {
        Carrito carrito = actualizarCantidadItemUseCase.execute(jwt.getSubject(), productoId, request.cantidad());
        return ResponseEntity.ok(carritoDtoMapper.toResponse(carrito));
    }

    @DeleteMapping("/items/{productoId}")
    public ResponseEntity<CarritoResponse> quitarItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productoId) {
        Carrito carrito = quitarItemDelCarritoUseCase.execute(jwt.getSubject(), productoId);
        return ResponseEntity.ok(carritoDtoMapper.toResponse(carrito));
    }

    @DeleteMapping
    public ResponseEntity<Void> vaciarCarrito(@AuthenticationPrincipal Jwt jwt) {
        vaciarCarritoUseCase.execute(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
