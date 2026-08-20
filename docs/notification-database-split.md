# Notificaciones en su propia base

Plan para separar los datos de notificaciones del resto del sistema, dejando el proceso donde
está. Escrito el 2026-08-20 para revisión; no ejecutado.

## Por qué este módulo y no otro

Los cuatro servicios extraídos (`order`, `inventory`, `product`, `payment`) **no se pueden separar
hoy**. Cada uno lee tablas que no le pertenecen:

| Servicio | Tablas que toca |
|---|---|
| order-service | `orders`, `order_items`, `payments`, `products`, `customer_addresses`, `system_settings` |
| inventory-service | `inventory_movements`, `products`, `categories` |
| product-service | `products`, `categories` |
| payment-service | `payments`, `orders` |

`products` la tocan tres servicios más el monolito. Separar la base bajo `order-service` lo deja
sin arrancar el mismo día. Se cortaron por capa técnica ("las lecturas de X") en vez de por
dominio con dueño, así que ninguno posee un conjunto propio de tablas.

`notification` sí:

- **Una sola tabla**: `notifications`.
- **Cero joins y cero relaciones JPA** con nada fuera del módulo.
- **Ninguna escritura transaccional**: el módulo solo declara `@Transactional(readOnly = true)`,
  en sus dos casos de uso de lectura. Lo que graba se dispara desde eventos de dominio, después
  del commit, por `AfterCommitPublisher` o por Kafka.

Ese último punto es el que abarata todo: mover la tabla a otra base **no parte ninguna
transacción**, porque hoy ya no comparte ninguna.

Segundo candidato, para después: `publication`, con cinco tablas todas bajo su propio prefijo.

## Datos primero, proceso después

Es el orden inverso al que se usó con los cuatro servicios actuales, y es a propósito.

Separar el proceso demuestra que se pueden hacer llamadas de red. **Separar los datos demuestra que
la frontera existe**: cuando la tabla vive en otra base, Postgres deja de permitir el join y nadie
puede cruzarla por descuido ni por apuro. La disciplina deja de ser necesaria porque el motor la
impone.

Mover el proceso después es cambiar una cadena de conexión.

## Qué se hace

### 1. La base

Misma instancia de Postgres, base nueva `pilarestilo_notifications`.

Una instancia aparte costaría entre 200 y 400 MB sin dar nada a cambio: la frontera la da la base,
no el proceso. La VPS tiene 12 GB con unos 4 en uso, así que el espacio no es la restricción.

**No usar esquemas separados.** Un esquema distinto dentro de la misma base sigue permitiendo el
join: parece una separación y no lo es, y al primer apuro alguien cruza la línea sin que nada avise.

### 2. El acceso

- Segundo `DataSource` en el monolito, acotado al paquete `notification`.
- `@EnableJpaRepositories` con `basePackages` y `entityManagerFactoryRef` propios, para que ningún
  repositorio de otro módulo pueda alcanzarlo por accidente.
- Directorio de Flyway propio (`db/migration-notifications`), con su historial separado. Dos bases,
  dos historiales: una migración de una nunca debe correr sobre la otra.

### 3. Los datos

`notifications` se mueve **con su historia**, no se recrea vacía:

```bash
pg_dump -t notifications --data-only pilarestilo | psql pilarestilo_notifications
```

Es el registro de qué se le envió a cada cliente. La confirmación escrita de la compra es prueba
que exige la Ley 21.398 — sin ella el derecho a retracto pasa de 10 a 90 días — así que borrarla
tiene un costo legal, no solo de comodidad.

El `DROP TABLE` en la base original va en una migración **posterior**, después de verificar que la
nueva responde. Nunca en el mismo paso.

### 4. La verificación

No basta con que compile:

- Un pedido real de punta a punta contra el stack local, comprobando que la notificación aparece en
  la base nueva y que el historial en Mi Cuenta la muestra.
- Que el registro de una clienta siga mandando su correo.
- Confirmar en la base vieja que la tabla ya no recibe filas.
- La suite completa de Playwright, que hoy toca notificaciones en varios caminos.

## Lo que hay que aceptar

- **Dos historiales de migración.** Flyway ya no cuenta una sola historia del esquema.
- **Dos respaldos.** El plan de respaldo pasa a cubrir dos bases; una sola olvidada es una pérdida
  silenciosa.
- **No hay join posible** entre una notificación y su pedido. Hoy no existe ninguno, y esa es la
  razón por la que este módulo es el candidato; pero cualquier pantalla futura que quiera cruzarlos
  tendrá que componer, no unir.

## Después, si funciona

Mover el módulo a proceso propio en la VPS del correo. Para entonces lo único que cambia es dónde
corre: la base ya está separada.

Lo que ese traslado sí necesita, y conviene no descubrir el día de: alcance de red entre los dos
hosts para Postgres y Kafka, y TLS entre ellos. **No** requiere tocar CSRF — esta API es sin estado
y lee el token solo de la cabecera `Authorization`, como está documentado en `SecurityConfig`.

Y hay un arreglo que conviene hacer en el mismo movimiento, porque hoy duele: el registro **manda
el correo de bienvenida dentro de la petición**, así que un proveedor lento hace lenta la respuesta.
Eso ya provocó fallos intermitentes en la suite y obligó a subir una espera de 20 a 40 segundos.
Con el módulo aparte, ese envío deja de estar en el camino del usuario.

## Fuera de alcance

- Los cuatro servicios actuales. Mientras no tengan dominio propio, no se les agrega
  funcionalidad ni se les separa la base: hoy son copias de consultas, no dueños de datos.
- Separar `publication`, que es el siguiente candidato y no este.
