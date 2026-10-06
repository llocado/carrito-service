package com.supermercado.carrito.infrastructure.persistence.repository;

import com.supermercado.carrito.infrastructure.persistence.entity.EventoProcesadoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEventoProcesadoRepository extends JpaRepository<EventoProcesadoEntity, UUID> {
}
