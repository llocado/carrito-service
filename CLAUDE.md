# carrito-service

Carrito de compra del proyecto supermercado, por usuario autenticado
(soporte de invitado diferido a propósito — ver ROADMAP.md). Reglas
compartidas con el resto del proyecto en `../CLAUDE.md` — este archivo solo
cubre lo específico de este repo.

Diseño completo del proyecto: `../app-productos/docs/ROADMAP.md`.

## Stack

- Spring Boot 4.1.0, Java 21, Gradle.
- Postgres 16 (contenedor `carrito-postgres`, puerto **5433** — el 5432 lo
  ocupa `productos-service`).
- Spring Security como OAuth2 Resource Server contra Keycloak
  (`supermercado-infra`, realm `supermercado`).
- RabbitMQ (vía `supermercado-infra`): publica `carrito.checkout-iniciado`
  en el exchange `carrito.events` con `POST /api/carrito/checkout` (202, 409 si
  el carrito está vacío, header opcional `Idempotency-Key`). Contrato en
  `docs/events/`. El checkout no modifica el carrito.
- Llama **sincrónicamente** a `productos-service` (RestClient) para obtener
  nombre/precio al agregar un item — decisión deliberada documentada en el
  ROADMAP, no un read-model poblado por eventos.
- Mantiene su propio read-model liviano de producto (`ItemCarrito`), no
  importa el dominio de `productos-service` ni `lib-domain-foods`.

## Comandos

```bash
docker compose up -d      # Postgres de este servicio
./gradlew bootRun          # arranca en localhost:8081
./gradlew test             # unitarios
./gradlew intTest          # integración (Testcontainers, requiere Docker)
./gradlew jacocoFullReport   # cobertura test + intTest -> build/reports/jacoco/jacocoFullReport/index.html
./gradlew pitest              # tests de mutacion (solo unitarios) -> build/reports/pitest/index.html
./gradlew sonar              # analisis SonarQube (ver ../CLAUDE.md: requiere SONAR_TOKEN y el perfil `calidad`)
```

Requiere `supermercado-infra` levantado (Keycloak + RabbitMQ) para validar
tokens y escuchar eventos de `productos-service`.

## Estado del repo (importante)

- Repo en GitHub (`llocado/carrito-service`), rama `main` con el código
  completo.
- Última vez verificado: 57 tests unitarios + 16 de integración pasando.
  Re-verificar con `./gradlew test intTest` antes de asumir que sigue así si
  pasó tiempo desde la última sesión.

## Convenciones propias de este servicio

- Valida el token de Keycloak directamente (no hay API Gateway todavía —
  ver ROADMAP, sección "Gateway: por qué se pospone").
- Escucha `pago.aprobado` de `pagos-service` (cola `carrito.pago-aprobado` +
  DLQ) y **descuenta del carrito solo lo comprado** (`Carrito.descontarComprado`),
  no lo vacía: lo agregado después del checkout se conserva. Idempotente por
  `eventId` (`eventos_procesados`). Politica de errores como en pedidos:
  payload inválido => DLQ; fallo transitorio => 1 intento + 3 reintentos.
