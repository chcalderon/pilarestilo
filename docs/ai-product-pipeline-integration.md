# Integracion IA de Productos (PR1: Diseno + Contratos)

## 1) Plan tecnico corto

1. Extender backend Spring Boot con un modulo `productai` (hexagonal), sin reemplazar stack.
2. Persistir drafts, assets y jobs en PostgreSQL (Flyway), con trazabilidad por `job_id`.
3. Ejecutar procesamiento IA en background con worker interno (`@Scheduled`) y reintentos con backoff.
4. Reusar storage actual (`MediaStorageService`) para originales/procesadas.
5. Integrar frontend admin (`/admin/products`) con flujo draft -> upload -> procesar -> revisar -> publicar.
6. Incorporar modo carga masiva por carpeta (seleccion multiarchivo desde navegador) + modo autorrelleno individual.

## 2) Stack actual detectado y decisiones de integracion

### Stack detectado en repo

- Frontend: Astro 4 + React islands + Tailwind + Zustand.
- Backend: Java 17 + Spring Boot 3.5.x + Spring Security JWT + JPA/Hibernate + Flyway.
- DB: PostgreSQL 16.
- Storage media: Local filesystem y S3-compatible via `MediaStorageService`.
- Mensajeria/eventos: `DomainEventPublisher` (in-process) + Kafka opcional.
- Observabilidad: Actuator + Prometheus + Grafana + OTel/Tempo.
- Edge: Caddy + rate limits en backend (`ApiGatewayRateLimitFilter`).
- CI/CD y despliegue: GitHub Actions + Docker Compose.

### Decisiones de integracion

- No crear framework nuevo de cola: se usa DB-backed jobs + worker Spring (`@Scheduled`), opcional evento de dominio al cerrar job.
- Reusar autenticacion/roles existentes: endpoints bajo `/api/admin/**` con `ADMIN` y `SELLER`.
- Reusar upload actual para media: originales y procesadas en carpetas dedicadas.
- Integrar el proyecto Node existente como motor IA intercambiable:
  - `engine=node_bridge` (invoca script Node existente).
  - `engine=openai_java` (port progresivo a Java con `RestClient`).
- Recomendacion inicial: partir con `node_bridge` por time-to-market, luego portar a Java manteniendo contrato de puerto.

## 3) Factibilidad (autorrelleno + carga masiva)

- **Autorrelleno individual**: factible y de bajo riesgo.
  - Usuario crea draft, sube imagen, dispara job IA, revisa propuesta y publica.
- **Carga masiva por carpeta desde Admin**: factible.
  - En web no se puede leer rutas del servidor por seguridad del navegador.
  - Solucion: seleccionar carpeta local con multiarchivo (input tipo directory) y subir lotes.
  - Convencion sugerida: `carpeta/subcarpeta_producto/*.jpg` para agrupar assets por producto.

## 4) Contratos API propuestos

Base path: `/api/admin/product-ai`

### 4.1 Crear producto borrador

`POST /drafts`

Request:

```json
{
  "name": "Vestido midi satinado",
  "brand": "Zara",
  "condition": "USED",
  "priceAmount": 39990,
  "priceCurrency": "CLP",
  "categoryIds": ["uuid-1", "uuid-2"]
}
```

Response `201`:

```json
{
  "draftId": "uuid",
  "productId": "uuid",
  "status": "DRAFT",
  "createdAt": "2026-04-30T22:10:00Z"
}
```

### 4.2 Subir imagenes originales (individual o lote)

`POST /drafts/{draftId}/images` (`multipart/form-data`)

Campos:
- `files[]`: una o multiples imagenes.
- `sourceFolder` (opcional): nombre de carpeta cliente para trazabilidad.

Response `201`:

```json
{
  "draftId": "uuid",
  "uploaded": [
    {
      "assetId": "uuid",
      "originalUrl": "/api/media/products/ai/original/....png",
      "filename": "look-01.jpg"
    }
  ]
}
```

### 4.3 Iniciar procesamiento IA

`POST /jobs`

Request:

```json
{
  "draftId": "uuid",
  "mode": "SINGLE",
  "targetWidth": 1024,
  "targetHeight": 1280,
  "strictGarmentFidelity": true,
  "forbidTextLogoWatermark": true
}
```

Response `202`:

```json
{
  "jobId": "uuid",
  "status": "PENDING"
}
```

### 4.3.b Inferencia 1-a-1 para formulario de `Productos` (sin n8n)

`POST /infer-single` (`multipart/form-data`)

Campos:
- `file`: imagen unica del producto.
- `brandHint` (opcional): ayuda contextual para mejorar titulo/descripcion.

Response `200`:

```json
{
  "title": "Blazer beige lino",
  "description": "Blazer de lino en excelente estado...",
  "imagePrompt": "Transformar imagen ... 4:5 ...",
  "engine": "ollama"
}
```

### 4.3.c Transformacion de imagen 1-a-1 para `Productos` (preview + reemplazo, sin n8n)

`POST /transform-single` (`multipart/form-data`)

Campos:
- `file`: imagen unica del producto.
- `provider` (opcional): `OPENAI` (default) o `OLLAMA` (experimental).
- `prompt` (opcional): prompt personalizado de transformacion.
- `brandHint` (opcional): contexto de marca para prompt default.

Response `200`:

```json
{
  "processedMasterUrl": "/api/media/products/ai/single/....-master.png",
  "processedWebUrl": "/api/media/products/ai/single/....-web.jpg",
  "processedThumbUrl": "/api/media/products/ai/single/....-thumb.jpg",
  "provider": "OPENAI",
  "promptUsed": "Generar una imagen de tamano ideal para Instagram...",
  "engine": "node_bridge"
}
```

Notas:
- El frontend usa `processedWebUrl` para preview en admin y permite reemplazar la imagen actual del formulario.
- `OLLAMA` para transformacion de imagen no esta soportado aun por `transform-images.js`; backend devuelve error controlado.

### 4.4 Estado de job (polling)

`GET /jobs/{jobId}`

Response `200`:

```json
{
  "jobId": "uuid",
  "draftId": "uuid",
  "status": "PROCESSING",
  "progress": 45,
  "attempt": 1,
  "maxAttempts": 3,
  "errorCode": null,
  "errorMessage": null,
  "startedAt": "2026-04-30T22:15:00Z",
  "finishedAt": null,
  "items": [
    {
      "assetId": "uuid",
      "title": "Vestido midi satinado...",
      "description": "....",
      "imagePrompt": "...",
      "processedUrl": "/api/media/products/ai/processed/....png"
    }
  ]
}
```

Estados soportados:
- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `ERROR`

### 4.5 Reintentar job con error

`POST /jobs/{jobId}/retry`

Response `202`:

```json
{
  "jobId": "uuid",
  "status": "PENDING",
  "attempt": 2
}
```

### 4.6 Aprobar y publicar producto

`POST /drafts/{draftId}/approve-publish`

Request:

```json
{
  "selectedAssetId": "uuid",
  "override": {
    "name": "Vestido midi satinado verde",
    "description": "Descripcion curada final"
  }
}
```

Response `200`:

```json
{
  "productId": "uuid",
  "status": "PUBLISHED",
  "publishedAt": "2026-04-30T22:25:00Z"
}
```

## 5) Estructura de carpetas propuesta

### Backend (Spring Boot)

```text
backend/src/main/java/com/pilarestilo/productai/
  domain/
    model/
    enums/
    ports/
    events/
  application/
    dto/
    commands/
    usecases/
    services/
  infrastructure/
    web/
      controllers/
      requests/
      responses/
    persistence/
      entities/
      repositories/
    ai/
      OpenAiJavaAdapter.java
      NodeBridgeAiAdapter.java
    jobs/
      ProductAiJobWorker.java
```

### Frontend (React islands dentro de Astro)

```text
frontend/src/islands/admin/product-ai/
  ProductAiPanel.tsx
  ProductAiDraftWizard.tsx
  ProductAiBulkFolderUpload.tsx
  ProductAiJobStatus.tsx
  ProductAiBeforeAfter.tsx
```

### Scripts (bridge opcional)

```text
scripts/ai-product-pipeline/
  package.json
  src/
    index.ts
    generate-copy.ts
    transform-image.ts
```

## 6) Migraciones y configuracion (propuesta)

Nueva migracion `V40__product_ai_pipeline.sql`:

- `product_ai_drafts`
  - `id`, `product_id`, `status`, `created_by`, `created_at`, `updated_at`
- `product_ai_assets`
  - `id`, `draft_id`, `original_url`, `processed_url`, `source_filename`, `sort_order`
- `product_ai_jobs`
  - `id`, `draft_id`, `status`, `attempt`, `max_attempts`, `next_retry_at`, `error_code`, `error_message`, `created_at`, `started_at`, `finished_at`
- `product_ai_outputs`
  - `id`, `job_id`, `asset_id`, `title`, `description`, `image_prompt`, `raw_response_json`
- Indices por `status`, `draft_id`, `next_retry_at`.

Configuracion `application.yml`:

- `app.product-ai.enabled`
- `app.product-ai.engine` (`node_bridge|openai_java`)
- `app.product-ai.openai.base-url`
- `app.product-ai.openai.api-key`
- `app.product-ai.openai.model`
- `app.product-ai.timeout-ms`
- `app.product-ai.max-attempts`
- `app.product-ai.retry-backoff-ms`
- `app.product-ai.worker.cron`
- `app.product-ai.image.target-width` (default `1024`)
- `app.product-ai.image.target-height` (default `1280`)
- `app.product-ai.image.web-width`
- `app.product-ai.image.web-height`
- `app.product-ai.image.web-jpeg-quality`
- `app.product-ai.image.thumb-width`
- `app.product-ai.image.thumb-height`
- `app.product-ai.image.thumb-jpeg-quality`
- `app.product-ai.node.command`
- `app.product-ai.node.project-path`
- `app.product-ai.node.generate-script`
- `app.product-ai.node.transform-script`
- `app.product-ai.node.workspace-root`

## 7) Variables de entorno requeridas

- `APP_PRODUCT_AI_ENABLED=true`
- `APP_PRODUCT_AI_ENGINE=node_bridge`
- `APP_PRODUCT_AI_OPENAI_API_KEY=...`
- `APP_PRODUCT_AI_OPENAI_BASE_URL=https://api.openai.com/v1`
- `APP_PRODUCT_AI_OPENAI_MODEL=gpt-image-1`
- `APP_PRODUCT_AI_OLLAMA_ENABLED=true`
- `APP_PRODUCT_AI_OLLAMA_BASE_URL=http://ollama:11434/api`
- `APP_PRODUCT_AI_OLLAMA_MODEL=gemma3`
- `APP_PRODUCT_AI_OLLAMA_AUTO_PULL_MODEL=true`
- `APP_PRODUCT_AI_OLLAMA_KEEP_ALIVE=45m`
- `APP_PRODUCT_AI_OLLAMA_VALIDATE_ON_STARTUP=true`
- `APP_PRODUCT_AI_OLLAMA_WARMUP_ON_STARTUP=true`
- `APP_PRODUCT_AI_OLLAMA_WARMUP_BLOCKING_ON_STARTUP=true`
- `APP_PRODUCT_AI_OLLAMA_WARMUP_TIMEOUT_MS=300000`
- `APP_PRODUCT_AI_OLLAMA_FAIL_FAST=false`
- `APP_PRODUCT_AI_TIMEOUT_MS=60000`
- `APP_PRODUCT_AI_MAX_ATTEMPTS=3`
- `APP_PRODUCT_AI_RETRY_BACKOFF_MS=2000`
- `APP_PRODUCT_AI_WORKER_CRON=*/20 * * * * *`
- `APP_PRODUCT_AI_IMAGE_TARGET_WIDTH=1024`
- `APP_PRODUCT_AI_IMAGE_TARGET_HEIGHT=1280`
- `APP_PRODUCT_AI_IMAGE_WEB_WIDTH=1024`
- `APP_PRODUCT_AI_IMAGE_WEB_HEIGHT=1280`
- `APP_PRODUCT_AI_IMAGE_WEB_JPEG_QUALITY=0.88`
- `APP_PRODUCT_AI_IMAGE_THUMB_WIDTH=320`
- `APP_PRODUCT_AI_IMAGE_THUMB_HEIGHT=400`
- `APP_PRODUCT_AI_IMAGE_THUMB_JPEG_QUALITY=0.82`
- `APP_PRODUCT_AI_NODE_COMMAND=node`
- `APP_PRODUCT_AI_NODE_PROJECT_PATH=E:/dev/pilarestilofotos`
- `APP_PRODUCT_AI_NODE_GENERATE_SCRIPT=generate-prompts.js`
- `APP_PRODUCT_AI_NODE_TRANSFORM_SCRIPT=transform-images.js`
- `APP_PRODUCT_AI_NODE_WORKSPACE_ROOT=./tmp/product-ai`

## 8) Comandos de ejecucion local (propuestos)

Backend:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Stack completo:

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
```

Ollama en red Docker (perfil `ai`):

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile ai up -d ollama
docker exec pe_ollama ollama pull gemma3
```

Comportamiento operacional:
- Al iniciar backend se valida conectividad de Ollama y disponibilidad del modelo configurado.
- Si `APP_PRODUCT_AI_OLLAMA_WARMUP_ON_STARTUP=true`, se dispara warmup del modelo.
- Si ademas `APP_PRODUCT_AI_OLLAMA_WARMUP_BLOCKING_ON_STARTUP=true`, backend espera warmup (hasta `APP_PRODUCT_AI_OLLAMA_WARMUP_TIMEOUT_MS`) para reducir error por cold start en la primera inferencia.
- Si falta servicio/modelo y `APP_PRODUCT_AI_OLLAMA_FAIL_FAST=false`, el backend levanta, pero el worker IA queda pausado hasta que Ollama este listo.
- Si `APP_PRODUCT_AI_OLLAMA_FAIL_FAST=true`, el backend aborta inicio cuando Ollama/modelo no estan disponibles.
- En Docker local, el endpoint `POST /api/admin/product-ai/infer-single` usa timeout de gateway 300s para cubrir carga inicial del modelo.

## 9) Checklist de pruebas

### Unitarias

- `CreateProductAiDraftUseCaseTest`
- `StartProductAiJobUseCaseTest`
- `ApprovePublishProductAiDraftUseCaseTest`
- `ProductAiRetryPolicyTest`
- `NodeBridgeAiAdapterTest` (mock process runner)
- `OpenAiJavaAdapterTest` (mock HTTP)

### Integracion

- `ProductAiControllerIT`:
  - create draft
  - upload imagenes
  - start job
  - poll status
  - retry
  - approve/publish
- `ProductAiJobWorkerIT`:
  - toma `PENDING`
  - procesa
  - persiste outputs
  - maneja error + reintento

### E2E minimo

- Admin crea draft en `/admin/products`.
- Sube carpeta con multiples imagenes.
- Job pasa `PENDING -> PROCESSING -> SUCCESS`.
- Admin revisa before/after y publica.
- Producto queda visible en catalogo publico.

## 10) Orden PR-style recomendado

1. **PR1 (este documento):** diseno y contratos.
2. **PR2:** backend (migracion + entidades + endpoints + worker base).
3. **PR3:** integracion motor IA (node bridge/openai) + retry robusto + logs estructurados por `job_id`.
4. **PR4:** frontend admin (wizard, carga carpeta, estado realtime por polling, aprobar/publicar).
5. **PR5:** pruebas unitarias/integracion/E2E + hardening.

## 11) Ajuste UX acordado (2026-04-30)

- `Productos` mantiene flujo rapido individual:
  - subir imagen unica
  - inferir/autorrellenar texto del formulario
- Nuevo modulo admin `Publicaciones e Imagenes` para flujo masivo:
  - tab `Carga masiva`
  - tab `Procesamiento IA`
  - tab `Campanas (n8n)`
- `Publicaciones e Imagenes` concentra:
  - seleccion por carpeta local para lotes
  - transformacion masiva IA de imagenes
  - optimizacion web (master + web + thumb)
  - descarga de imagen final desde admin
  - orquestacion de campanas IG/FB por n8n

## 12) Estado de implementacion actual (PR3)

- Implementado backend base en `productai`:
  - migracion `V40__product_ai_pipeline.sql`
  - endpoints `/api/admin/product-ai/*`
  - scheduler async (`ProductAiJobScheduler`)
- Integrado motor real `node_bridge` con proyecto externo `E:\dev\pilarestilofotos`:
  - ejecuta `generate-prompts.js` y `transform-images.js` por `job_id`
  - genera y persiste derivados `master/web/thumb` por cada asset
  - aplica compresion JPEG para `web` y `thumb`
  - valida formatos de entrada compatibles (`jpg/png/webp`)
- Implementada conexion frontend real en `/admin/publicaciones`:
  - crear draft
  - subir lote de imagenes
  - iniciar job
  - listar/reintentar/consultar jobs
  - aprobar/publicar directamente desde jobs `SUCCESS`
- Pendiente PR4:
  - subflujo rapido en `Productos` para inferir/autorrellenar formulario desde imagen unica
  - publicacion automatizada de campanas n8n desde assets aprobados
