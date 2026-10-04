# Eventos de carrito-service

Contrato del bus de eventos (RabbitMQ) segun lo definido en el ROADMAP del proyecto (seccion "Contrato de eventos"). Cada evento va en el envelope comun (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `correlationId`, `payload`).

## Publica (productor)

- **`carrito.checkout-iniciado`** — [carrito.checkout-iniciado.schema.json](./carrito.checkout-iniciado.schema.json). Exchange `carrito.events` (topic), routing key = `eventType`. Se publica desde `IniciarCheckoutUseCase` cuando el usuario llama a `POST /api/carrito/checkout`. Consumido por `pedidos-service`, que crea el pedido.
  - `eventId` y `correlationId` son el id del intento de checkout (`checkoutId`), el mismo que devuelve el endpoint.
  - **Idempotencia:** si el cliente envia el header opcional `Idempotency-Key` (UUID), el `eventId` se deriva de `usuario + clave`. Reintentar con la misma clave produce el mismo `eventId` y `pedidos-service` lo descarta como duplicado. Sin header, cada llamada genera un `eventId` nuevo.
  - **El carrito no se modifica** al iniciar el checkout: se vacia recien cuando el pago se aprueba (politica del ROADMAP).

## Consume

Nada todavia. A futuro: el evento de pago aprobado, para vaciar el carrito.

## Limitaciones conocidas

- La publicacion no es transaccional con nada (el checkout solo lee el carrito), pero tampoco hay confirmacion de entrega: si el exchange no tiene ninguna cola bindeada (por ejemplo, `pedidos-service` nunca arranco), RabbitMQ descarta el mensaje en silencio. Una solucion futura es el patron outbox o `publisher confirms` + `mandatory`.
