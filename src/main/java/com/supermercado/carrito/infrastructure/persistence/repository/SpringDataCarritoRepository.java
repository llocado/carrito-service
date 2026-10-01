package com.supermercado.carrito.infrastructure.persistence.repository;

import com.supermercado.carrito.infrastructure.persistence.entity.CarritoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataCarritoRepository extends JpaRepository<CarritoEntity, UUID> {

    Optional<CarritoEntity> findByUsuarioId(String usuarioId);
}
