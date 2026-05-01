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
- PR3 backend integration completed (`node_bridge`):
  - bridge to external project `E:\dev\pilarestilofotos`
  - runs `generate-prompts.js` + `transform-images.js` in async worker
  - persists processed URLs `master/web/thumb` per asset
  - web and thumb derivatives are JPEG-compressed for faster admin/catalog load
  - guards duplicate publish on already-published drafts
- Incremental Java migration applied:
  - new `ProductAiOllamaClient` in backend Java for text inference (title/description/imagePrompt)
  - `Productos` 1-a-1 now calls endpoint `infer-single` (solo texto, sin transformacion de imagen, sin n8n)
  - `Publicaciones` jobs now use Ollama text inference + ChatGPT image transform (node bridge actual)
- `Productos` 1-a-1 expanded with transform preview flow:
  - new endpoint `POST /api/admin/product-ai/transform-single`
  - prompt personalizable (con default de invierno + fidelidad de prenda)
  - selector de proveedor en UI (`OPENAI` / `OLLAMA`)
  - preview transformada + accion `Reemplazar imagen actual`
  - enlace de descarga directa de preview en admin
  - derivadas optimizadas (`master/web/thumb`) para carga web
  - timeout de gateway extendido en Caddy para `POST /api/admin/product-ai/transform-single` (180s)
- Hardening de inferencia Ollama en `Productos` 1-a-1:
  - `keep_alive` configurable para mantener modelo caliente
  - warmup de Ollama al inicio de backend
  - warmup bloqueante opcional con timeout configurable (default activo en Docker)
  - timeout de gateway extendido para `POST /api/admin/product-ai/infer-single` (300s)
  - mensaje UX de timeout actualizado para explicar cold start (2-4 min)
- Estado proveedor transformacion:
  - `OPENAI`: operativo en pipeline actual (`transform-images.js`)
  - `OLLAMA`: visible en UI como experimental, backend responde error controlado (aun no soportado por bridge actual)
- n8n campaign workflow automation still pending (next stage).

### Next (PR4)

1. Implementar transformacion de imagen con proveedor local (Ollama/Comfy bridge) para reemplazar el fallback experimental.
2. Add campaign queue entity + endpoint to pass approved assets to n8n webhook.
3. Add first automated n8n campaign flow using approved assets.
4. Add integration test coverage for `node_bridge` happy path + error/retry path.
