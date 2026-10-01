package com.supermercado.carrito.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "carritos")
@Getter
@Setter
@NoArgsConstructor
public class CarritoEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, unique = true, length = 100)
    private String usuarioId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    // Sin @Setter: se muta en el lugar (clear/add) para que Hibernate detecte
    // huerfanos correctamente. Reasignar la referencia (setItems) rompe eso.
    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ItemCarritoEntity> items = new ArrayList<>();
}
