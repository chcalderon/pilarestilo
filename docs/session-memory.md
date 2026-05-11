# Session Memory

Bitacora de contexto tecnico para retomar trabajo sin perder continuidad.

## Uso recomendado

- PowerShell: `./scripts/dev/save_session_memory.ps1 "nota corta del avance"`
- Bash: `bash ./scripts/dev/save_session_memory.sh "nota corta del avance"`

Registrar al menos:

- antes de cerrar una sesion larga
- despues de cambios funcionales grandes
- antes y despues de levantar/reconstruir Docker
## 2026-05-10 20:06:57 -04:00

- Branch: `master`
- Commit: `9cbb935`
- Note: Modal checkout: header visible, cierre X, scripts local rebuild
- Working tree:
```text
 M README.md
 M docs/architecture.md
 M docs/deployment.md
 M frontend/src/components/Navbar.astro
 M frontend/src/islands/CartPage.tsx
 M frontend/src/islands/admin/AdminLoginForm.tsx
 M frontend/src/middleware.ts
 M frontend/src/styles/globals.css
?? .claude/
?? docs/session-memory.md
?? frontend/test-results/
?? frontend/tmp-cart-after-rebuild.png
?? frontend/tmp-cart-with-item.png
?? scripts/deploy/local_rebuild.ps1
?? scripts/deploy/local_rebuild.sh
?? scripts/dev/
?? tmp-cart.html
```

