package com.supermercado.carrito.infrastructure.persistence.adapter;

import com.supermercado.carrito.application.port.CarritoRepositoryPort;
import com.supermercado.carrito.domain.model.Carrito;
import com.supermercado.carrito.infrastructure.persistence.entity.CarritoEntity;
import com.supermercado.carrito.infrastructure.persistence.mapper.CarritoEntityMapper;
import com.supermercado.carrito.infrastructure.persistence.repository.SpringDataCarritoRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CarritoPersistenceAdapter implements CarritoRepositoryPort {

    private final SpringDataCarritoRepository springDataCarritoRepository;
    private final CarritoEntityMapper carritoEntityMapper;

    @Override
    public Carrito guardar(Carrito carrito) {
        // Se busca la entidad ya gestionada (si existe) en vez de construir una
        // nueva y hacer save() a ciegas: asi el mapper muta la coleccion de
        // items de una entidad que Hibernate ya conoce, y orphanRemoval borra
        // correctamente los items que ya no estan en el agregado de dominio.
        CarritoEntity entity = springDataCarritoRepository.findById(carrito.getId().getValor())
                .orElseGet(CarritoEntity::new);

        carritoEntityMapper.volcarEnEntidad(carrito, entity);

        CarritoEntity guardada = springDataCarritoRepository.save(entity);
        return carritoEntityMapper.toDomain(guardada);
    }

    @Override
    public Optional<Carrito> buscarPorUsuarioId(String usuarioId) {
        return springDataCarritoRepository.findByUsuarioId(usuarioId)
                .map(carritoEntityMapper::toDomain);
    }
}
