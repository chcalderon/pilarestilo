# Protección de datos (Ley 21.719)

Rige el **1 de diciembre de 2026**. Entre esa fecha y diciembre de 2027 las PYMEs reciben
amonestación en vez de multa; eso es un año de margen, no una exención.

## Qué guarda la tienda, y por qué

| Dato | Dónde | Por qué se conserva |
|---|---|---|
| Nombre, correo, teléfono | `users` | Identificar a la clienta y contactarla por su compra |
| Direcciones de despacho | `customer_addresses` | Enviar el pedido |
| Pedidos e items | `orders`, `order_items` | Ejecutar y respaldar la venta |
| Comprobantes de transferencia | disco privado, fuera de `/api/media` | Verificar el pago; llevan datos bancarios |
| Boletas | `sales_documents` + disco privado | **Obligación tributaria: 6 años** |
| Datos bancarios de reembolso | `return_requests`, cifrados | Devolver el dinero; se borran al cerrar el reembolso |
| Consentimientos | `data_consents` | Probar qué se aceptó y bajo qué versión |

## Consentimiento

`data_consents` es **append-only**. Cada fila guarda el tipo (`TERMS`, `PRIVACY`, `MARKETING`), la
**versión publicada** del texto, la fecha, la IP y el user-agent. Retirar el consentimiento marca
`revoked_at`; nunca borra la fila, porque "consintió el 3 y lo retiró el 9" es el hecho que importa
y una fila borrada no dice ninguna de las dos mitades.

Las versiones vigentes viven en `system_settings.privacy_policy_version` / `terms_version`. Subir
una versión convierte todo consentimiento anterior en "dado bajo un texto más antiguo", que es
exactamente la pregunta que hay que poder responder. Por eso el formulario de configuración **no**
las toca: publicar una versión nueva es un acto deliberado con un texto detrás.

`TERMS` y `PRIVACY` se registran al crear la cuenta. `MARKETING` es aparte y se da y se retira desde
`/api/me/privacy/marketing`.

## Derechos ARCOP

- **Acceso**: `GET /api/me/privacy/export` devuelve todo lo que la tienda tiene de esa clienta —
  cuenta, pedidos, direcciones, reseñas, devoluciones y consentimientos — como archivo.
- **Supresión**: `POST /api/me/privacy/deletion` deja la solicitud en cola. La resuelve el admin en
  `/api/admin/privacy/deletions`, y **anonimiza, no borra**:

  | Se va | Se queda |
  |---|---|
  | Correo, nombre, teléfono, avatar de la cuenta | La fila del usuario (pedidos y boletas apuntan a ella) |
  | Todas las direcciones de despacho | Pedidos, pagos y documentos tributarios |
  | La cuenta queda desactivada | Las reseñas, que no llevan nombre propio: el autor sale del usuario |

  La cuenta pasa a `anonimo+<id>@pilarestilo.invalid` y "Cliente anonimizado". Es irreversible a
  propósito. Las boletas siguen legibles porque guardan copia del nombre y correo del comprador
  desde que se emiten — ese snapshot existe exactamente para este momento.

  Ver los permisos: `privacy.read` para ver la cola, `privacy.resolve` para ejecutarla; separados
  porque anonimizar no se deshace.

## Brecha de datos

Si hay acceso no autorizado, pérdida o divulgación de datos personales:

1. **Contener** — cortar el acceso, rotar credenciales (`JWT_SECRET`, `SYSTEM_SETTINGS_CRYPTO_SECRET`,
   claves SMTP y de pasarela), y dejar el sistema en estado seguro antes de investigar.
2. **Registrar** — qué datos, de cuántas personas, desde cuándo, y cómo se detectó. Sin registro no
   hay notificación defendible.
3. **Notificar a la Agencia de Protección de Datos sin dilación**, y a las personas afectadas cuando
   el riesgo sea alto. El plazo de referencia es **72 horas** desde que se toma conocimiento.
4. **Corregir** — cerrar la causa, no solo el síntoma, y anotar qué cambió.

Encargado de datos: la dueña de la tienda (`admin@pilarestilo.com`) hasta que se designe a otra
persona; el nombre tiene que estar publicado en la política de privacidad.

## Lo que ya se cerró

- Los comprobantes de pago salieron de `/api/media/**` (público) a un directorio privado leído solo
  por endpoint autenticado — commit `4c6c40e`.
- Los datos bancarios de reembolso se guardan cifrados y se borran al cerrar el reembolso.
- Las boletas guardan copia del comprador para que una anonimización futura no las deje ilegibles.

## Lo que falta

- Pantalla de la política de privacidad versionada en la tienda, enlazada desde el registro.
- Pantalla del admin para la cola de supresión (el backend ya está; espera a que aterrice Astro).
- Casilla de marketing en el registro (hoy el consentimiento existe en el backend, sin UI).
