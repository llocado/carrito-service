# carrito-service

Microservicio de carrito de compras del proyecto supermercado. Arquitectura
hexagonal, mismo estilo que `productos-service` (`app-productos`): casos de
uso en `application/usecase`, puertos en `application/port`, adaptadores en
`infrastructure/*`.

Ver `docs/ROADMAP.md` en el repo `app-productos` para el diseño completo del
proyecto (decisiones de arquitectura, mapa de servicios, contrato de eventos).

## Decisiones de diseño propias de este servicio

- **Un usuario, un carrito.** No hay historial de carritos por usuario; se
  reutiliza el mismo registro (`usuario_id` es único).
- **Snapshot de producto al agregar, no read-model por eventos.** Al agregar
  un item, `carrito-service` le pregunta a `productos-service` (REST síncrono,
  `GET /api/productos/{id}`) el nombre y precio actuales, y los guarda como
  "foto" en el item. Se descartó mantener un read-model propio alimentado por
  RabbitMQ para esto: `productos-service` no publica eventos de creación
  retroactivos, así que un read-model 100% event-driven partiría "ciego" a
  cualquier producto creado antes de que existiera el listener. RabbitMQ
  queda reservado para más adelante (vaciar el carrito cuando `pagos-service`
  confirme un pago).
- **`carrito-service` no importa el dominio de `productos-service`.** Su
  propio modelo (`Carrito`, `ItemCarrito`) es una representación mínima
  propia — duplicación intencional, no un descuido (ver ROADMAP.md).
- **El id de usuario nunca viene del cliente.** Siempre sale del claim `sub`
  del JWT validado por Spring Security — evita que un usuario opere sobre el
  carrito de otro.

## Requisitos para correr localmente

1. `supermercado-infra` levantado (Keycloak + RabbitMQ): `docker compose up -d`
   en ese repo.
2. Este repo tiene su propio Postgres (puerto **5433**, no choca con el 5432
   de `productos-service`): `docker compose up -d` en este repo.
3. `productos-service` corriendo en `localhost:8080` (necesario para poder
   agregar items al carrito — `carrito-service` lo consulta en cada alta).

```bash
docker compose up -d          # Postgres de este servicio
./gradlew bootRun             # arranca en localhost:8081
```

## Endpoints

Todos requieren `Authorization: Bearer <token>` con rol `cliente` (ver
`bruno/Carrito/00 - Obtener Token.bru` para conseguir uno de prueba contra el
realm `supermercado` de Keycloak).

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/carrito` | Ver el carrito del usuario autenticado |
| POST | `/api/carrito/items` | Agregar un producto (incrementa si ya estaba) |
| PUT | `/api/carrito/items/{productoId}` | Fijar la cantidad de un item |
| DELETE | `/api/carrito/items/{productoId}` | Quitar un item |
| DELETE | `/api/carrito` | Vaciar el carrito completo |

## Tests

```bash
./gradlew test      # unitarios (dominio, casos de uso, slice de controller)
./gradlew intTest    # integracion con Testcontainers (requiere Docker)
```
