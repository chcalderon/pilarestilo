# Documentacion vs Fuente - Auditoria 2026-05-09

## Alcance

Revision cruzada entre documentacion vigente (`README.md`, `CLAUDE.md`, `docs/*.md` operativos) y fuente actual en:

- `backend/src/main/java`
- `backend/src/main/resources/application.yml`
- `infra/docker-compose.yml`
- `infra/Caddyfile`
- `scripts/deploy/*.sh` y `scripts/deploy/*.ps1`
- `.github/workflows/*.yml`

Nota: `docs/superpowers/plans` y `docs/superpowers/specs` se mantienen como historico y no se reescriben en esta auditoria.

## Desfases detectados y correcciones aplicadas

| Documento | Desfase detectado | Accion |
|---|---|---|
| `docs/auth.md` | Faltaba endpoint real `POST /api/auth/google` en tabla de auth | Endpoint agregado |
| `docs/auth.md` | Faltaba `PUT /api/auth/me/avatar` en lista de endpoints sensibles autenticados sin `@PreAuthorize` | Endpoint agregado |
| `docs/auth.md` | Numeracion repetida (`## 6` duplicado) | Numeracion corregida |
| `docs/operations-api.md` | Faltaban endpoints reales recientes de media admin (`resize-products-categories-15cm`, `hero-models`) | Endpoints y descripcion agregados |
| `docs/deployment.md` | Comandos de backup usaban ruta inconsistente `/opt/PilarEstilo` | Actualizado a `/opt/pilarestilo` |
| `docs/github-actions-vps.md` | Ejemplos y checklist usaban `/opt/PilarEstilo` | Actualizado a `/opt/pilarestilo` |
| `CLAUDE.md` | `APP_INVENTORY_REMOTE_ENABLED` descrito como delegacion de lecturas | Corregido: delega comandos de escritura de inventario (`reserve/release/confirm`) |

## Observaciones de contexto (sin cambio funcional de codigo)

- `PAYMENT_GATEWAY_PROVIDER` mantiene soporte runtime para `STUB` y `MERCADO_PAGO` en `application.yml`.
- En settings de dominio, la lista habilitable de proveedores de pasarela esta restringida a `MERCADO_PAGO`.
- `APP_PRODUCT_AI_ENGINE` tiene fallback `stub` en `application.yml`, mientras `infra/.env.example` y `docker-compose` priorizan `openai_backend` para despliegues Docker.

## Estado final

- Documentacion operativa y de seguridad alineada con la fuente al 2026-05-09.
