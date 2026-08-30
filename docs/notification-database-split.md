# Notificaciones en su propia base

Plan para separar los datos de notificaciones del resto del sistema, dejando el proceso donde
está. Escrito el 2026-08-20 para revisión.

> **Estado 2026-08-30 (histórico):** ejecutado y superado. `notification-service` corre en su
> propio proceso con su propia base desde el 2026-08-29. Y los cuatro shims que este doc usa de
> contraejemplo (`order`/`inventory`/`product`/`payment-service`) fueron consolidados de vuelta al
> monolito el 2026-08-30 — nunca tuvieron datos propios. El texto de abajo se conserva como el
> argumento original de por qué `notification` era el candidato correcto.

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
- **Ninguna transacción compartida**: ninguna transacción escribe hoy `notifications` y otra tabla
  a la vez. Lo que graba se dispara desde eventos de dominio, después del commit, por
  `AfterCommitPublisher` o por Kafka.

Ese último punto es el que abarata todo: mover la tabla a otra base **no parte ninguna
transacción**, porque hoy ninguna cruza la frontera.

No es que el módulo no escriba: hay ocho `@Transactional` de escritura —tres en
`NotificationRepositoryAdapter`, `MarkNotificationReadUseCase`, `MarkAllNotificationsReadUseCase` y
dos ramas de `KafkaPaymentNotificationListener`—. Lo que ninguna hace es mezclar tablas de los dos
lados, y esa es la propiedad que importa. La más cercana al límite es `onPaymentSubmitted`, que
escribe `orders` vía `UpdateOrderStatusUseCase` pero no graba notificación alguna: avisa a los
revisores por correo.

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

- Segundo `DataSource` en el monolito, acotado al paquete `com.pilarestilo.notifications`.
- `@EnableJpaRepositories` con `basePackages` y `entityManagerFactoryRef` propios, para que ningún
  repositorio de otro módulo pueda alcanzarlo por accidente. El principal necesita lo suyo también:
  si se queda con `com.pilarestilo`, se lleva `NotificationJpaRepository` al `EntityManagerFactory`
  equivocado.
- Directorio de Flyway propio (`db/migration-notifications`), con su historial separado. Dos bases,
  dos historiales: una migración de una nunca debe correr sobre la otra.
- Un `PlatformTransactionManager` por `DataSource`, y **cada `@Transactional` de escritura que
  alcance el repositorio de notificaciones tiene que nombrar el suyo**. Sin calificador se queda
  con el principal, que gobierna la base vieja, y la escritura cae fuera de la transacción que se
  creyó abrir. La incómoda es `KafkaPaymentNotificationListener.onPaymentConfirmed`: una sola
  transacción que lee `orders` y `users` de la base vieja y graba `notifications` en la nueva.
  Ahí hay que separar la lectura de la escritura, no calificar la anotación y seguir.

### 2b. La parte espinosa: el EntityManager principal

Este es el detalle que decide cuánto cuesta el cambio, y conviene saberlo antes de empezar.

Con dos `DataSource` hacen falta dos `EntityManagerFactory`. Spring Boot, por defecto, escanea
`com.pilarestilo` entero, así que `NotificationEntity` quedaría mapeada **también** en el
principal. Con `ddl-auto: validate`, eso hace que el arranque exija la tabla `notifications` en la
base vieja — justo la que se va a eliminar. El backend no levanta.

Es decir: no basta con agregar el segundo `DataSource`; hay que **decirle al principal que no mape
esa entidad**, y Spring no ofrece un "escanea todo menos esto".

Las salidas, con su costo:

- **Listar los paquetes del principal, uno por uno.** Hoy son 24 (`billing`, `cashregister`,
  `category`, …). Funciona y es explícito, pero cada módulo nuevo que alguien agregue y olvide
  listar desaparece del mapeo en silencio. Cambia un problema por otro del mismo tipo.
- **Mover la entidad fuera del árbol escaneado**, a un paquete raíz propio, y darle a cada
  `EntityManagerFactory` su raíz: dos listas de una línea cada una, y un módulo nuevo cae en el
  principal por defecto. Por sí sola no alcanza. `packagesToScan` incluye por prefijo y de forma
  recursiva, así que `com.pilarestilo.notifications` —el nombre correcto para el dominio— sigue
  cayendo dentro de la raíz del principal, `com.pilarestilo`, y la entidad vuelve a quedar mapeada
  dos veces. Escapar de verdad obligaría a una raíz fuera de `com.pilarestilo`, es decir a un
  nombre de paquete que miente sobre el dominio.
- Un `PersistenceManagedTypes` a medida que filtre. Más código propio para un problema que las
  otras dos resuelven sin escribir código.

**Decidido: las dos primeras juntas.** La entidad y su repositorio JPA se mueven a
`com.pilarestilo.notifications.persistence` —el paquete correcto para el dominio, y una lista de
una línea para el `EntityManagerFactory` de notificaciones— y el principal enumera sus 24 paquetes
de módulo.

El silencio de la enumeración se tapa con un test: compara los subdirectorios de `com/pilarestilo/`
contra la lista del `EntityManagerFactory` principal, y falla si alguno no está ni en esa lista ni
en la de notificaciones. Así el módulo nuevo que nadie listó deja de ser un arranque roto en
producción y pasa a ser un test rojo en la máquina de quien lo agregó.

Mover el paquete no cambia comportamiento por sí solo: `com.pilarestilo.notifications` sigue bajo
la raíz de escaneo por defecto, así que mientras haya un solo `DataSource` todo queda igual. Nada
fuera del módulo referencia la entidad ni el repositorio —se verificó sobre `backend`, sus tests y
`services/`— así que el cambio queda contenido.

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
