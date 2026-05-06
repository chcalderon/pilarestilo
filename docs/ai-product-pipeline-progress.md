# IA Product Pipeline - Progress Log

## 2026-05-01

### Completed

- PR1 design + API contracts documented in:
  - `docs/ai-product-pipeline-integration.md`
- Documentation/source audit refreshed and aligned:
  - `docs/documentation-source-diff-2026-04-30.md`
- Stack version mismatch fixed in docs (`Spring Boot 3.5`):
  - `README.md`
  - `docs/architecture.md`
  - `backend/README.md`
- Admin UX base for agreed split flow implemented:
  - Kept `Productos` for single-item flow
  - Added new admin module `Publicaciones e Imagenes`:
    - `frontend/src/pages/admin/publicaciones.astro`
    - `frontend/src/islands/admin/PublicacionesMediaPage.tsx`
  - Added sidebar entry in:
    - `frontend/src/islands/admin/AdminSidebar.tsx`
  - Added dashboard quick link in:
    - `frontend/src/islands/admin/AdminDashboard.tsx`

### Current status

- PR2 backend core implemented:
  - Flyway `V40__product_ai_pipeline.sql`
  - module `productai` with drafts/assets/jobs/outputs
  - async scheduler (`ProductAiJobScheduler`)
  - endpoints under `/api/admin/product-ai`
- Frontend `Publicaciones e Imagenes` wired to real backend APIs:
  - create draft
  - upload images
  - start job
  - list/poll/retry jobs
  - download processed image URL
  - approve/publish from successful jobs
- `Productos` ahora incluye switch de UX para flujo individual:
  - `carga manual` tradicional
  - `nuevo flujo IA (1 imagen)` para inferir texto/imagen sin pasar por n8n
- PR3 backend integration completed (backend-only):
  - pipeline sin dependencia de proyecto externo
  - `master/web/thumb` se persisten por asset
  - `web` y `thumb` se comprimen para carga mas rapida en admin/catalogo
  - se evita publish duplicado en drafts ya publicados
- Product AI migration consolidada en Java:
  - `ProductAiOpenAiClient` para inferencia (title/description/imagePrompt)
  - `Productos` 1-a-1 usa `infer-single`
  - `Publicaciones` jobs operan en modo backend-only (`openai_backend`)
- `Productos` 1-a-1 expanded with transform preview flow:
  - new endpoint `POST /api/admin/product-ai/transform-single`
  - prompt personalizable (con default de invierno + fidelidad de prenda)
  - proveedor soportado en UI: `OPENAI`
  - preview transformada + accion `Reemplazar imagen actual`
  - enlace de descarga directa de preview en admin
  - derivadas optimizadas (`master/web/thumb`) para carga web
  - timeout de gateway extendido en Caddy para `POST /api/admin/product-ai/transform-single` (180s)
- timeout de gateway extendido para `POST /api/admin/product-ai/infer-single` (300s)
- Estado proveedor transformacion:
  - `OPENAI`: operativo en pipeline actual.
- n8n campaign workflow automation still pending (next stage).

### OpenAI migration (2026-05-01)

- Ollama stack eliminado del pipeline de Product AI.
- Engine default: `openai_backend` (antes `ollama_backend`).
- `ProductAiOpenAiClient` ahora usa modelos separados:
  - `APP_PRODUCT_AI_OPENAI_INFER_MODEL` (default `gpt-4.1-mini`) para inferencia de texto.
  - `APP_PRODUCT_AI_OPENAI_IMAGE_MODEL` (default `gpt-image-1`) para generacion de imagen.
  - `APP_PRODUCT_AI_OPENAI_MODEL` conservado como fallback legacy para `image-model`.
- Errores de API OpenAI ahora exponen `error.message` del cuerpo de la respuesta.

### Next (PR4)

1. Add campaign queue entity + endpoint to pass approved assets to n8n webhook.
2. Add first automated n8n campaign flow using approved assets.
3. Add integration test coverage for `openai_backend` happy path + error/retry path.
