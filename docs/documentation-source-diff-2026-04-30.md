# Documentacion vs Fuente - Auditoria 2026-04-30 / actualizada 2026-05-01

## Alcance

Revision cruzada entre documentacion (`README.md`, `docs/*.md`, `backend/README.md`) y codigo fuente actual (`backend`, `infra`, `services`, `frontend`).

## Resultado 2026-04-30

- No se encontraron desfases funcionales criticos entre rutas/API, migraciones, eventos de dominio y configuracion de despliegue.
- Se detecto y corrigio un desfase de version de framework:
  - Documentos con "Spring Boot 3.3"
  - Fuente real: `spring-boot-starter-parent` en `3.5.14` (backend y microservicios)
  - Ajuste aplicado: `README.md`, `docs/architecture.md`, `backend/README.md` actualizados a `Spring Boot 3.5`

## Actualizacion 2026-05-01

Desfases detectados tras commit `9f15e36` (remove ollama stack / switch to openai backend) y cambios unstaged de model split:

| Documento | Desfase | Accion |
|---|---|---|
| `docs/ai-product-pipeline-integration.md` sec. 6 | `app.product-ai.openai.model` era la unica clave; faltan `infer-model` e `image-model` | Agregados los dos nuevos keys; `model` marcado como legacy |
| `docs/ai-product-pipeline-integration.md` sec. 7 | `APP_PRODUCT_AI_ENGINE=ollama_backend` + 15 vars de Ollama inexistentes en fuente | Reemplazado por `openai_backend` + vars `INFER_MODEL` / `IMAGE_MODEL`; vars Ollama eliminadas |
| `docs/ai-product-pipeline-integration.md` sec. 8 | Bloque de comandos Ollama (`--profile ai`, `pe_ollama`) | Eliminado; reemplazado por nota de comportamiento OpenAI |
| `docs/deployment.md` | `DEPLOY_PROFILES=microservices,cache,ai` y ejemplo `--profile ai up -d ollama` | `ai` removido de perfiles; ejemplo Ollama eliminado |
| `docs/ai-product-pipeline-progress.md` PR4 | Primer item mencionaba Ollama/Comfy bridge | Reemplazado por n8n + cobertura openai_backend |
| `CHANGELOG.md` [Unreleased] | Sin entrada para Ollama removal, model split ni mejor error handling | Entradas Added/Changed/Removed agregadas |

## Estado final

- Documentacion alineada con fuente al cierre de esta actualizacion (2026-05-01).
