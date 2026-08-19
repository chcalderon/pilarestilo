# Plan: subir Astro 4 → 7

**Hazlo en un chat nuevo.** No porque sea difícil, sino porque es ancho: toca configuración,
adaptador SSR, middleware, integraciones y todas las islas a la vez, y conviene empezar con el
contexto limpio y esta página delante.

## Por qué se hace

Los seis avisos de seguridad que quedan cuelgan todos de aquí: `astro`, `@astrojs/node`, `esbuild`
(vía vite) y `sharp`. Ninguno se arregla sin subir el major. El resto de las dependencias ya está
al día (commit `21973ef`).

Estado actual:

| | Versión | Al día |
|---|---|---|
| astro | 4.16.19 | 7.x |
| @astrojs/node | 8.3.4 | 11.x |
| @astrojs/react | 3.6 | acompaña |
| @astrojs/tailwind | 5.1 | **eliminado en Astro 5**: pasa a `@tailwindcss/vite` |
| tailwindcss | 3.4 | 4.x, con cambios de sintaxis |

## Cómo hacerlo

**Un major a la vez**, verificando entre cada uno. Saltar de 4 a 7 de una vez deja sin saber cuál
de los tres rompió qué.

```bash
cd frontend
npx @astrojs/upgrade          # herramienta oficial: sube astro + integraciones juntas
npx tsc --noEmit && npx vitest run && npm run build
```

Después de cada major, **caminar la tienda en Docker**, no solo compilar:

```bash
cd infra && docker compose --env-file .env --profile kafka --profile cache \
  --profile microservices up -d --build frontend
```

## Lo que hay que mirar en este repo

Ordenado por riesgo real, no por lo que dicen las guías genéricas.

1. **`src/middleware.ts` (199 líneas)** — es lo más expuesto. Guarda `/admin/*` (valida el JWT
   contra el backend) y `/{locale}/checkout` (chequeo local, deliberadamente sin llamar al backend).
   Astro 5 cambió `astro:middleware` y el orden de `sequence`. Verificar los cuatro caminos:
   admin sin token, admin con token expirado, backend caído (debe **mantener** la cookie), y
   checkout sin sesión.

2. **`output: 'server'` + `@astrojs/node` en `standalone`** — Astro 5 eliminó `hybrid` y cambió la
   semántica de `output`; las páginas ahora son estáticas salvo que se marquen. Con 34 páginas
   `.astro` que leen del backend en SSR, revisar que ninguna quede prerenderizada por accidente:
   una ficha de producto congelada muestra stock viejo.

3. **`@astrojs/tailwind` desaparece en Astro 5.** Migrar a `@tailwindcss/vite` y Tailwind 4, que
   cambia la sintaxis de configuración (`@theme` en CSS en vez de `tailwind.config.mjs`). Ojo con
   `darkMode: ['selector', '[data-theme="dark"]']`, que es como el admin y la tienda cambian de
   tema.

4. **39 directivas `client:*` en las islas** — hidratación no cambió de API, pero sí de tiempos.
   Revisar el carrito, el selector de variantes y el checkout, que dependen de estado de Zustand
   hidratado.

5. **`i18n` con `routing: 'manual'`** — la API de i18n se movió entre 4 y 5; con enrutado manual
   el impacto es menor, pero `/es/` y `/en/` tienen que seguir resolviendo.

6. **`sharp`** — lo usa el pipeline de imágenes; sube con Astro y cambia el manejo de formatos.
   Revisar que las imágenes de producto sigan optimizándose.

## Qué verificar antes de dar por bueno

Caminar, no asumir:

- Home, catálogo, ficha de producto (con variantes), carrito.
- Checkout completo: envío → pago → resumen, con el aviso de retracto **antes** del botón de pagar.
- Mi Cuenta: pedidos, subir comprobante, botón de arrepentimiento.
- Admin: login, Productos (crear y editar, con el selector de tipo de variante), Ventas,
  Devoluciones, Configuración.
- `npx vitest run` — 91 tests, incluidos los dos del formulario de producto.
- Modo oscuro en admin y tienda.

## Salida

Un commit por major, cada uno con la tienda caminada. Si un major se atasca, se queda en el
anterior: dos majores arriba ya cierran cuatro de los seis avisos.
