# Documentacion vs Fuente - Auditoria 2026-04-30

## Alcance

Revision cruzada entre documentacion (`README.md`, `docs/*.md`, `backend/README.md`) y codigo fuente actual (`backend`, `infra`, `services`).

## Diferencias encontradas

| ID | Documento (estado actual) | Fuente (estado real) | Impacto | Ajuste recomendado |
|---|---|---|---|---|
| D1 | `backend/README.md:294` indica que las migraciones llegan hasta `V27`. | Existen migraciones posteriores hasta `V39` (ejemplo: `backend/src/main/resources/db/migration/V39__dispatches.sql:1`). | Alto: da una foto incompleta del esquema y de features productivas (caja, despachos, permisos). | Actualizar la seccion "Database migrations" para cubrir `V28` a `V39`. |
| D2 | `README.md:77`, `docs/architecture.md:194`, `backend/README.md:167` indican que `/api/orders*` se enruta directo a `order-service`. | En `infra/Caddyfile` las lecturas (`GET/HEAD`) van a `order-service` (`:90-95`), pero escrituras (`POST/PATCH`) van a `backend` (`:106-113`). | Alto: puede romper troubleshooting e integraciones por suponer entrypoint incorrecto en writes. | Corregir redaccion para separar claramente reads vs writes en rutas de ordenes. |
| D3 | `docs/deployment.md:431` referencia `application-local.properties`. | El perfil local existente es YAML: `backend/src/main/resources/application-local.yml:1`. | Medio: confunde setup local y debugging de configuracion. | Cambiar referencia de `application-local.properties` a `application-local.yml`. |
| D4 | `docs/domain-events.md:17` solo documenta `DiscountApplied` y marca suscriptores `none`. | El dominio tambien publica `DiscountCodeAssigned` (`.../DiscountCodeAssigned.java:7`) y existe listener activo `DiscountNotificationListener` (`.../DiscountNotificationListener.java:27`). | Alto: la documentacion omite un evento en uso con impacto en notificaciones. | Agregar `DiscountCodeAssigned` al catalogo y registrar su subscriber real. |
| D5 | `docs/domain-events.md:12` resume `OrderStatusChanged` con "shipped notification". | El listener maneja al menos dos ramas: `PREPARING_ORDER` (`OrderNotificationListener.java:42`) y `SHIPPED` (`:60`). | Medio: describe parcialmente el comportamiento real de notificaciones de orden. | Actualizar subscriber/descripcion de `OrderStatusChanged` para incluir `PREPARING_ORDER` y `SHIPPED`. |

## Resultado de verificacion

- La fuente si esta mas avanzada que parte de la documentacion.
- El desfase principal esta en ruteo de ordenes, catalogo de eventos y resumen de migraciones.
