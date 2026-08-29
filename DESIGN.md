---
name: Pilar Estilo
description: Boutique de moda circular chilena — elegante, cercana, sustentable
colors:
  rose: "#B76E79"
  rose-deep: "#8E4F58"
  rose-soft: "#E8C9CC"
  gold: "#C6A96B"
  ink: "#1A1A1A"
  charcoal: "#3A3A3A"
  warm-cream: "#F2EAE0"
  mid-cream: "#E3D2BE"
  parchment: "#F5F1EB"
  pure-white: "#FFFFFF"
  positive-ink: "#03513A"
  positive-surface: "#E6F3EC"
  danger-ink: "#8F2D3B"
  danger-surface: "#FBE9EC"
  warning-ink: "#7A4E15"
  warning-surface: "#FBF0DC"
typography:
  display:
    fontFamily: "Cormorant Garamond, Georgia, serif"
    fontSize: "clamp(2.5rem, 7vw, 5rem)"
    fontWeight: 300
    lineHeight: 1.05
    letterSpacing: "0.01em"
  headline:
    fontFamily: "Cormorant Garamond, Georgia, serif"
    fontSize: "clamp(1.5rem, 4vw, 2.5rem)"
    fontWeight: 400
    lineHeight: 1.2
  title:
    fontFamily: "Montserrat, system-ui, sans-serif"
    fontSize: "0.95rem"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "0.18em"
  body:
    fontFamily: "Montserrat, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 300
    lineHeight: 1.7
  label:
    fontFamily: "Montserrat, system-ui, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: "0.28em"
rounded:
  none: "0px"
  sm: "2px"
  md: "4px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "48px"
  2xl: "80px"
components:
  button-primary:
    backgroundColor: "{colors.rose}"
    textColor: "{colors.pure-white}"
    rounded: "{rounded.none}"
    padding: "14px 48px"
    typography: "{typography.label}"
  button-primary-hover:
    backgroundColor: "{colors.rose-deep}"
    textColor: "{colors.pure-white}"
    rounded: "{rounded.none}"
    padding: "14px 48px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "14px 48px"
  button-ghost-hover:
    backgroundColor: "transparent"
    textColor: "{colors.rose}"
    rounded: "{rounded.none}"
    padding: "14px 48px"
  badge-used:
    backgroundColor: "{colors.rose-soft}"
    textColor: "{colors.rose-deep}"
    rounded: "{rounded.none}"
    padding: "3px 8px"
  badge-new:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.warm-cream}"
    rounded: "{rounded.none}"
    padding: "3px 8px"
---

# Design System: Pilar Estilo

## 1. Overview

**Creative North Star: "La Vitrina Curada"**

Pilar Estilo es la vitrina de una boutique de barrio que llegó al mundo digital sin perder su alma. La persona detrás de la curaduría eligió cada pieza: la fotografió, la describió, decidió que merece estar ahí. El sistema de diseño debe hacer sentir ese criterio en cada pantalla. No es una grilla de productos; es una selección.

La paleta es parchment y crema cálida con un acento de rosa empolvado: el color de un probador con buena luz. El oro aparece apenas, como un pendiente. La tipografía es Cormorant para los títulos — serif de revista de moda, ligero y alto — y Montserrat en letra pequeña, espaciada, casi como etiquetas de percha. Juntas dicen "conocemos nuestro oficio". El movimiento es suave y deliberado: nada rebota, nada parpadea. Las prendas entran al frame como entrarías a un espacio tranquilo.

Este sistema rechaza explícitamente: la grilla infinita sin jerarquía (Shein, Falabella marketplace), el lujo frío e inaccesible (Gucci.com), la familiaridad genérica del retail americano (Forever 21, H&M), y cualquier cosa que haga pensar "lo armó una IA esta tarde".

**Key Characteristics:**
- Superficie parchment dominante; el rosa es acento, nunca fondo
- Tipografía serif de peso ligero para display; sans espaciada para labels y CTA
- Bordes rectos (radius: 0); la curvatura viene del contenido, no del chrome
- Sombras reservadas para hover de producto; el resto es plano
- Ritmo asimétrico: mismo padding en todas partes es monotonía

## 2. Colors: La Paleta de la Vitrina

Color strategy: **Restrained**. Las superficies son crema y parchment. El rosa (`#B76E79`) ocupa menos del 10% de cualquier pantalla. El oro es raro, casi nunca.

### Primary
- **Rosa Empolvado** (`#B76E79`): el acento central. CTAs primarios, precios en oferta, hover states, badges de descuento. Nunca como fondo de página o sección completa.
- **Rosa Profundo** (`#8E4F58`): hover y estado activo del rosa empolvado. Badge USADO texto. Nunca como color principal en UI en reposo.
- **Rosa Suave** (`#E8C9CC`): fondo de badge USADO. Highlights sutiles. Nunca texto.

### Secondary
- **Oro Cálido** (`#C6A96B`): tocado de lujo accesible. Ratings stars, detalles decorativos, hover en elementos de navegación secundaria. No más de 2-3 instancias por pantalla.

### Neutral
- **Tinta** (`#1A1A1A`): texto primario, iconos, botones ghost. Nunca puro negro.
- **Carbón** (`#3A3A3A`): texto secundario, precios de lista tachados (con opacity 0.5), iconos muted.
- **Crema Cálida** (`#F2EAE0`): superficie principal del admin y hover states claros. Fondo alternado en secciones del storefront.
- **Crema Media** (`#E3D2BE`): skeleton loaders, placeholder de imagen, separadores.
- **Parchment** (`#F5F1EB`): fondo base del storefront. El lienzo de la vitrina.
- **Blanco Puro** (`#FFFFFF`): superficie de cards de producto, inputs, modales.

**La Regla del Acento Único.** El rosa aparece en ≤10% de cualquier pantalla. Su rareza es el punto. Cuando todos los CTAs, todos los badges y todos los hovers son rosas, el rosa deja de significar algo.

**La Regla del Oro Escaso.** El dorado aparece máximo 2-3 veces por pantalla. Si puedes contar más de 3 instancias, elimina todas menos una.

### Estados: Positivo / Peligro / Advertencia

Tres familias semánticas, la misma forma de tres roles, todas ligadas al tema (`globals.css` +
`tailwind.config.mjs`). Reemplazan los pares `text-{red,green,amber}-500 dark:text-…-300` y
`bg-…-50 dark:bg-…-900/30` hechos a mano por componente, en una docena de tonos.

| rol | qué es | positivo | peligro | advertencia |
|---|---|---|---|---|
| **`-ink`** | texto + íconos legibles en cualquier superficie de su tema (flip por tema) | `#03513A` → `#06B382` | `#8F2D3B` → `#FCA5A5` | `#7A4E15` → `#F0C674` |
| **bare** (`rgb(… / <alpha-value>)`) | relleno sólido del chip (blanco encima despeja 4.5:1) + base de los bordes `/NN` de alerta; **constante** | `rgb(3 81 58)` | `rgb(143 45 59)` | `rgb(180 83 9)` |
| **`-surface`** | fondo del panel de alerta (flip por tema) | `#E6F3EC` → `#122019` | `#FBE9EC` → `#39191E` | `#FBF0DC` → `#332612` |

`text-pe-positive` es el nombre viejo de `text-pe-positive-ink`.

Son colores de **estado, no de marca**: no cuentan contra la Regla del Acento Único, pero un panel
de peligro o advertencia visible en pantalla ya es señal de que algo pasó. No decorar con ellos.

**Nunca** una franja lateral (`border-l`/`border-r` > 1px de color) como acento en una alerta —
borde completo + la `-surface` correspondiente.

## 3. Typography: Dos Voces, Un Oficio

**Display Font:** Cormorant Garamond (Georgia, serif)
**Body/Label Font:** Montserrat (system-ui, sans-serif)
**Acento Decorativo:** Pinyon Script (uso estrictamente ornamental: hero de landing, firma de marca)

**Carácter:** Cormorant trae el ojo editorial de una revista de moda femenina; Montserrat trae la claridad de una etiqueta de percha bien diseñada. No son opuestos — son colaboradores. Una habla de aspiración; la otra habla de información.

### Hierarchy
- **Display** (weight 300, clamp 2.5rem→5rem, line-height 1.05): nombres de categoría héroe, headline de landing, título de sección con foto de fondo. Solo Cormorant, siempre ligero.
- **Headline** (weight 400, clamp 1.5rem→2.5rem, line-height 1.2): títulos de página, nombre de sección secundaria. Cormorant, regularidad sin rigidez.
- **Title** (Montserrat weight 500, 0.95rem, letter-spacing 0.18em): nombres de marca en cards, títulos de columna en tablas, labels de sección en admin. Uppercase siempre.
- **Body** (Montserrat weight 300, 0.875rem, line-height 1.7): descripciones de producto, texto informativo, párrafos. Máx 65ch por línea.
- **Label** (Montserrat weight 500, 0.72rem, letter-spacing 0.28em, uppercase): botones CTA, badges de condición, tags de filtro, placeholders de input. El espaciado amplio es firma del sistema.

**La Regla del Serif Ligero.** Cormorant en weight 300-400, nunca bold. Si un título necesita fuerza, gánala con tamaño, no con peso. Un Cormorant en bold pierde todo lo que lo hace elegante.

**La Regla del Uppercase Espaciado.** Montserrat en labels y CTAs va siempre en uppercase con letter-spacing ≥0.18em. Sin uppercase y sin tracking, Montserrat pequeño es invisible. Con ambos, es firma.

## 4. Elevation

Plano por defecto. Las superficies descansan en el mismo plano; la profundidad se logra con color (parchment → crema → blanco) y espaciado, no con sombras. Las sombras son respuesta al estado, no decoración de reposo.

### Shadow Vocabulary

- **Card Lift** (`0 18px 40px -22px rgba(143, 79, 88, 0.35)`): exclusivo para hover de cards de producto. La sombra toma el tono del rosa profundo — el producto se eleva hacia ti, no flota sobre un gris neutro.
- **Editorial** (`0 30px 60px -30px rgba(26, 26, 26, 0.25)`): para modales, drawers y elementos de overlay. Difusa, suave, no dramática.

**La Regla Plano-por-Defecto.** Ningún elemento tiene sombra en reposo excepto modales/drawers abiertos. Si algo tiene sombra en reposo, está compitiendo con el hover del producto. El hover del producto siempre gana.

## 5. Components

### Buttons

El botón es una instrucción, no una decoración. Sin radius. Sin gradiente. La acción comunica con tipografía espaciada y color, no con forma ornamental.

- **Shape:** sin radius (0px). Bordes rectos siempre.
- **Primary:** fondo rosa empolvado (`#B76E79`), texto blanco, padding 14px 48px, label uppercase 0.28em tracking. Hover → rosa profundo (`#8E4F58`), transición 0.2s ease-out.
- **Ghost:** sin fondo, borde 1px tinta (`#1A1A1A`), texto tinta. Hover → borde y texto rosa empolvado. Misma tipografía que primary.
- **Focus-visible:** outline 2px rosa a 3px de distancia. Nunca quitar el focus ring.
- **Disabled:** opacity 0.38, cursor not-allowed, sin hover.

### Badges de Condición

El estado USADO/NUEVO no se disculpa; se anuncia. Posicionado absolute sobre la imagen de producto.

- **USADO:** fondo rosa suave (`#E8C9CC`), texto rosa profundo (`#8E4F58`). Calidez; la pieza tiene historia.
- **NUEVO:** fondo tinta (`#1A1A1A`), texto crema cálida (`#F2EAE0`). Contraste máximo; distinción inmediata.
- **Shape:** sin radius. Montserrat label uppercase, 0.18em tracking.

### Product Cards

La card no grita; invita. El hover sube la pieza hacia el usuario con la sombra rosa.

- **Corner Style:** radius 6px (`rounded-md`), en la card y en la máscara de imagen. Excepción deliberada a la regla general de "sin radius": pedida por el dueño para que la vitrina se sienta menos angular sin perder el borde recto de botones, badges e inputs — la suavidad vive en el contenedor, no en la acción. Nunca más de 6px; una card no es un ícono de app.
- **Background:** blanco puro para la superficie; parchment como placeholder de imagen.
- **Image:** aspect-ratio 3:4, object-fit cover. En hover: scale(1.04) con transition 0.4s.
- **Hover:** translateY(-4px) + card-lift shadow. Solo transform + box-shadow; nunca layout properties.
- **Brand:** Montserrat 0.65rem weight 500, uppercase, letra rosa. Siempre sobre el nombre.
- **Name:** Montserrat 0.8rem weight 300, tinta.
- **Pricing:** precio de lista tachado en carbón opacity 0.5; precio de venta en tinta weight 500; porcentaje de descuento en rosa.
- **Internal Padding:** 12px top, 4px horizontal, 16px bottom.

### Icon Buttons

Un ícono solo, sin caja, no es un botón — es un glifo flotando que además falla el mínimo táctil. Todo ícono clickeable (tema, buscar, menú, carrito, favoritos, cerrar) vive dentro de un área circular.

- **Shape:** `rounded-full`. Es la misma excepción que las product cards: el contenedor se suaviza, el ícono adentro no cambia.
- **Hit area:** 44×44px mínimo siempre, aunque el glifo visual sea más chico (18-20px) — el padding hace la diferencia, no el ícono.
- **Estado:** fondo transparente en reposo; `hover:` un tinte rosa suave (`pe-rose-soft` en claro, equivalente atenuado en oscuro) más color de ícono a rosa. El mismo lenguaje que ya usaba el botón `icon` de `Button.astro`; esto lo extiende a la navegación, que antes no lo usaba.

### Inputs / Fields

Sharp. Sin radius. El foco cambia el borde a rosa/50 — no un glow, solo un cambio de color.

- **Style:** fondo blanco, borde 1px rgba(26,26,26,0.15), sin radius, Montserrat 0.78rem weight 300.
- **Placeholder:** carbón opacity 0.45 (no más bajo — debe ser legible).
- **Focus:** borde cambia a rgba(183,110,121,0.5). Sin shadow, sin glow.
- **Error:** borde rojo semántico, texto de error debajo del campo, nunca solo encima.
- **Disabled:** opacity 0.38, cursor not-allowed.

### Navigation — Storefront

La navegación es el marco de la vitrina: presente pero no protagonista.

- **Background:** parchment (`#F5F1EB`) en modo claro; oscuro (`#161313`) en modo oscuro.
- **Logo:** Cormorant Garamond display, acompañado de Pinyon Script como acento ornamental opcional.
- **Links:** Montserrat weight 300, uppercase, letter-spacing 0.18em, tinta. Hover → rosa empolvado, transition 0.2s.
- **Compact (scroll):** altura reducida, transition de altura suave. El logo escala; el menú permanece.
- **Mobile:** drawer lateral con overlay. Bottom navigation prohibido para el storefront (no es una app de tareas).

### Categoria Pills / Filter Chips

- **Style:** sin radius, borde 1px carbón opacity 0.3, fondo transparente, texto label uppercase.
- **Selected:** fondo tinta, texto crema cálida, sin borde.
- **Hover:** borde rosa empolvado, texto rosa.

## 6. Do's and Don'ts

### Do:
- **Do** usar Cormorant en weight 300-400 para todos los headings display. El peso ligero es la firma.
- **Do** mantener el rosa empolvado en ≤10% de cualquier pantalla. Su rareza es lo que lo hace funcionar.
- **Do** usar radius 0 en todos los elementos interactivos: botones, inputs, cards, badges, modales. Las curvas vienen del contenido (fotos de prendas), no del chrome.
- **Do** reservar las sombras exclusivamente para hover de producto y modales abiertos.
- **Do** animar solo con `transform` y `opacity`. Las propiedades de layout (width, height, padding) no se animan.
- **Do** usar `ease-out` con curvas como `cubic-bezier(0.22, 0.61, 0.36, 1)` para entradas. Las salidas van más rápido que las entradas.
- **Do** escribir labels, badges y CTAs en uppercase con letter-spacing ≥0.18em. Sin tracking, el texto pequeño desaparece.
- **Do** presentar el producto como protagonista: imagen grande en aspecto 3:4, nombre breve, precio claro. La card sirve al producto, no al revés.
- **Do** destacar el origen circular de las prendas como valor: el badge USADO es un argumento, no una disculpa.

### Don't:
- **Don't** usar gradiente de texto (`background-clip: text`). Nunca. El sistema usa color sólido.
- **Don't** usar `border-left` o `border-right` mayor a 1px como acento de color en cards o alertas. Reemplazar con tinte de fondo o sin borde.
- **Don't** anidar cards. Una card dentro de una card siempre es el diseño incorrecto.
- **Don't** hacer que el sistema parezca fast fashion masivo (Shein, H&M, Forever 21): nada de grids infinitos sin jerarquía, nada de tipografía genérica sin aire, nada de rojo de "OFERTA" agresivo.
- **Don't** hacer que el sistema parezca lujo inaccesible (Gucci, Louis Vuitton): sin distancia, sin frialdad, sin blanco clínico con gold pesado.
- **Don't** hacer que parezca un marketplace genérico (MercadoLibre, Falabella): sin tablas de comparación de precios, sin categorías colapsadas, sin design utilitario.
- **Don't** disculpar la naturaleza de segunda mano del inventario. USADO es una categoría de valor, no un disclamer.
- **Don't** usar glassmorfismo. No pertenece a este sistema.
- **Don't** usar el hero-metric template (número grande + label pequeño + gradiente acento). Es el cliché SaaS; no aplica aquí.
- **Don't** poner más de 3 instancias del color dorado en una misma pantalla.
- **Don't** usar emoji como iconos de interfaz. Lucide React está disponible; usarlo.
