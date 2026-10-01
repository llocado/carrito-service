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
```

Requiere `supermercado-infra` levantado (Keycloak + RabbitMQ) para validar
tokens y escuchar eventos de `productos-service`.

## Estado del repo (importante)

- Rama `main` con el código completo commiteado localmente, **sin remoto
  configurado** (nada pusheado).
- Última vez verificado: 42 tests unitarios + 9 de integración pasando.
  Re-verificar con `./gradlew test intTest` antes de asumir que sigue así si
  pasó tiempo desde la última sesión.

## Convenciones propias de este servicio

- Valida el token de Keycloak directamente (no hay API Gateway todavía —
  ver ROADMAP, sección "Gateway: por qué se pospone").
- Escucha eventos de `productos-service` (cambios de precio/disponibilidad)
  y, más adelante, el evento de pago aprobado para vaciarse (política de
  vaciado ya decidida en el ROADMAP).
