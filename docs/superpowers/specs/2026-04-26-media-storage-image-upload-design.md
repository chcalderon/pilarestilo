# Media Storage Abstraction & Image Upload Design

## Goal

Replace hardcoded local filesystem writes with a pluggable `MediaStoragePort` (LOCAL + S3_COMPATIBLE adapters). Add drag & drop image upload to categories (currently URL-only) and refine the product image upload. Migrate existing category images from external URLs (Unsplash) to local storage. Remove the "Ruta activa" label from both image upload forms.

## Architecture

```
MediaStoragePort (interface, shared/domain/ports)
  ├── String store(InputStream data, String folder, String filename, String contentType)
  └── void delete(String folder, String filename)

LocalFileStorageAdapter   → writes to MEDIA_STORAGE_PATH/{folder}/{filename}
                            returns /api/media/{folder}/{filename}

S3StorageAdapter          → uploads to S3-compatible bucket at {folder}/{filename}
                            returns {s3PublicBaseUrl}/{folder}/{filename}
                            throws DescriptiveException if credentials not configured

MediaStorageService       → reads systemSettings.mediaStorageProvider at runtime
                            delegates to LocalFileStorageAdapter or S3StorageAdapter
                            (both beans always registered; selection is runtime)

MediaUploadController     → injected with MediaStorageService (replaces raw Path writes)
MediaResourceConfig       → registers /api/media/** static handler only when provider=LOCAL
                            (S3 URLs are public and served directly from the bucket)
```

## Tech Stack

- **Backend**: Spring Boot 3, Java 17, hexagonal architecture
- **S3 SDK**: `software.amazon.awssdk:s3` (version managed by Spring Boot BOM or pinned)
- **Frontend**: React + TypeScript in Astro island
- **Upload endpoint**: existing `POST /api/media/upload?folder={folder}` (no change to contract)

---

## Section 1: Backend Storage Abstraction

### Port

```java
// shared/domain/ports/MediaStoragePort.java
public interface MediaStoragePort {
    String store(InputStream data, String folder, String filename, String contentType);
    void delete(String folder, String filename);
}
```

### LocalFileStorageAdapter

Extracts current `MediaUploadController` filesystem logic into a dedicated `@Component`. Writes to `Paths.get(storagePath).resolve(folder).resolve(filename)`. Returns `/api/media/{folder}/{filename}`.

### S3StorageAdapter

`@Component` using `S3Client` (AWS SDK v2). Reads credentials from `SystemSettings` at call time:
- endpoint, region, bucket, accessKeyId, secretKey (decrypted), pathStyleEnabled, publicBaseUrl
- Returns `{s3PublicBaseUrl}/{folder}/{filename}`
- Throws `IllegalStateException("S3 not configured")` if bucket or credentials are blank

### MediaStorageService

`@Service` that loads `SystemSettings` on each call and delegates:

```java
public String store(MultipartFile file, String folder) {
    var settings = systemSettingsRepository.find();
    var port = settings.mediaStorageProvider() == S3_COMPATIBLE ? s3Adapter : localAdapter;
    return port.store(file.getInputStream(), folder, generateFilename(file), file.getContentType());
}
```

### MediaUploadController changes

Remove `Path storagePath` field and all `Files.*` calls. Inject `MediaStorageService`. Delegate `store()` call. Response format unchanged: `MediaUploadResponse(url, filename, size)`.

### MediaResourceConfig changes

No change needed. The `/api/media/**` static resource handler stays registered unconditionally. When S3 is active, stored URLs are full public S3 URLs and never hit this handler — its presence is harmless.

---

## Section 2: Category Image Migration (one-time)

### Endpoint

`POST /api/admin/media/migrate-category-images` — `ADMIN` role required.

Logic:
1. Load all categories
2. For each with `image_url` that does NOT start with `/api/media/` (i.e., external URL):
   - Download the external URL with a 10-second timeout
   - Generate filename: `category-{id}.{ext}` (ext derived from Content-Type or URL)
   - Store via `MediaStorageService` with `folder="categories"`
   - Update category `image_url` in DB to the returned storage URL
3. Return `{ migrated: N, failed: N, errors: [{ categoryId, url, reason }] }`
4. Idempotent: categories already pointing to `/api/media/` are skipped

### Admin UI

Button "Migrar imágenes de categorías" in `SystemSettingsPanel` under the media storage section. Shows spinner while running, then success/error summary. Uses new API function `migrateCategoryImages(token)`.

---

## Section 3: Frontend `ImageDropzone`

### New component: `frontend/src/islands/admin/ImageDropzone.tsx`

Props:
```typescript
interface ImageDropzoneProps {
  value?: string;          // current image URL for preview
  onUpload: (url: string) => void;
  folder: string;          // "categories" | "products"
  token: string;
  label?: string;          // optional label above zone
}
```

States:
- **idle**: shows preview (if `value` exists) with change overlay; or placeholder icon + "Arrastrá o hacé clic para subir"
- **dragging**: border highlight, background tint
- **uploading**: spinner overlay on preview/placeholder
- **error**: red border, error message below zone

Behavior:
- Click anywhere on zone → `<input type="file" accept="image/*">` programmatic click
- `dragenter/dragover` → dragging state
- `drop` → extract file, upload
- `onChange` on hidden input → upload
- Upload calls existing `POST /api/media/upload?folder={folder}` via `uploadMediaFile(file, folder, token)` (new function in `api.ts`)
- On success → `onUpload(url)` + show preview
- On error → show error message, stay in error state (user can retry)

### New API function in `api.ts`

```typescript
export async function uploadMediaFile(file: File, folder: string, token: string): Promise<string> {
  const form = new FormData();
  form.append('file', file);
  form.append('folder', folder);
  const res = await apiFetch<MediaUploadDto>('/media/upload', {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });
  return res.url;
}

export async function migrateCategoryImages(token: string): Promise<{ migrated: number; failed: number; errors: unknown[] }> {
  return apiFetch('/admin/media/migrate-category-images', {
    method: 'POST',
    headers: authHeaders(token),
  });
}
```

### CategoryTree.tsx changes

In the category create/edit form, replace the `<input type="text">` for "URL imagen" with:
```tsx
<ImageDropzone
  folder="categories"
  value={form.imageUrl}
  onUpload={url => setForm(f => ({ ...f, imageUrl: url }))}
  token={token}
/>
```

Remove the URL text input entirely.

### ProductForm.tsx changes

1. Replace the current file upload button section (lines ~470–522) with `<ImageDropzone folder="products" value={previewUrl} onUpload={url => setPreviewUrl(url)} token={token} />`
2. Delete the "Ruta activa" label block (lines ~480–484): the `<p>Ruta activa</p>` and the URL display `<p>{previewUrl}</p>` below it
3. The `previewUrl` state is kept — `ImageDropzone` uses it internally for the preview; no need to show it as raw text

---

## File Structure

### New backend files
```
shared/domain/ports/MediaStoragePort.java
shared/infrastructure/adapters/LocalFileStorageAdapter.java
shared/infrastructure/adapters/S3StorageAdapter.java
shared/infrastructure/services/MediaStorageService.java
```

### New backend files (continued)
```
shared/infrastructure/web/controllers/MediaAdminController.java    (migrate-category-images endpoint)
```

### Modified backend files
```
shared/infrastructure/web/controllers/MediaUploadController.java   (use MediaStorageService)
```

> `MediaResourceConfig` needs no changes — see Section 1.

### New frontend files
```
frontend/src/islands/admin/ImageDropzone.tsx
```

### Modified frontend files
```
frontend/src/islands/admin/CategoryTree.tsx       (replace URL input with ImageDropzone)
frontend/src/islands/admin/ProductForm.tsx        (replace upload section, remove Ruta activa)
frontend/src/lib/api.ts                           (uploadMediaFile, migrateCategoryImages)
```

---

## Error Handling

- **Upload fails (network/server)**: `ImageDropzone` shows error message inline, zone stays interactive for retry
- **S3 not configured**: `S3StorageAdapter.store()` throws `IllegalStateException` → controller returns 500 with clear message; admin sees error in UI
- **Category image download fails during migration**: logged + included in `errors[]` response; other categories continue
- **File too large / wrong type**: existing Spring validation (10 MB, image/* only) unchanged; `ImageDropzone` passes `accept="image/*"` to input

## Testing

- `MediaStorageServiceTest`: mock both adapters, verify LOCAL setting delegates to local adapter, S3 setting delegates to S3 adapter
- `LocalFileStorageAdapterTest`: write to temp dir, assert file exists and returned URL matches `/api/media/{folder}/{filename}`
- `CategoryImageMigrationTest`: mock HTTP download + `MediaStorageService`, verify DB updated, skips already-migrated
- TypeScript compile check on `ImageDropzone.tsx`, `CategoryTree.tsx`, `ProductForm.tsx`
