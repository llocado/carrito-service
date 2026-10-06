# Eventos de carrito-service

Contrato del bus de eventos (RabbitMQ) segun lo definido en el ROADMAP del proyecto (seccion "Contrato de eventos"). Cada evento va en el envelope comun (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `correlationId`, `payload`).

## Publica (productor)

- **`carrito.checkout-iniciado`** — [carrito.checkout-iniciado.schema.json](./carrito.checkout-iniciado.schema.json). Exchange `carrito.events` (topic), routing key = `eventType`. Se publica desde `IniciarCheckoutUseCase` cuando el usuario llama a `POST /api/carrito/checkout`. Consumido por `pedidos-service`, que crea el pedido.
  - `eventId` y `correlationId` son el id del intento de checkout (`checkoutId`), el mismo que devuelve el endpoint.
  - **Idempotencia:** si el cliente envia el header opcional `Idempotency-Key` (UUID), el `eventId` se deriva de `usuario + clave`. Reintentar con la misma clave produce el mismo `eventId` y `pedidos-service` lo descarta como duplicado. Sin header, cada llamada genera un `eventId` nuevo.
  - **Una clave por intento:** reutilizarla (tras cambiar el carrito o para reintentar tras un pago fallido) devuelve el `checkoutId` original y `pedidos-service` descarta el evento como duplicado.
  - **El carrito no se modifica** al iniciar el checkout: recien cuando el pago se aprueba se descuenta lo comprado (ver `pago.aprobado` mas abajo).

## Consume

- **`pago.aprobado`** — contrato en `pagos-service/docs/events/pago.aprobado.schema.json` (la fuente de verdad es el productor). Exchange `pagos.events` (topic), cola `carrito.pago-aprobado` (con `carrito.pago-aprobado.dlq`). Por cada item pagado se resta la cantidad comprada del carrito del usuario (y se elimina el item si llega a cero); lo agregado despues del checkout se conserva. Idempotente por `eventId` (tabla `eventos_procesados`). Un payload invalido (campos obligatorios, usuario de hasta 100 caracteres, hasta 500 items, cantidades positivas) va a la DLQ sin reintentos; un fallo transitorio se reintenta 1 + 3 veces.

## Limitaciones conocidas

- La publicacion no es transaccional con nada (el checkout solo lee el carrito), pero tampoco hay confirmacion de entrega: si el exchange no tiene ninguna cola bindeada (por ejemplo, `pedidos-service` nunca arranco), RabbitMQ descarta el mensaje en silencio. Una solucion futura es el patron outbox o `publisher confirms` + `mandatory`.
