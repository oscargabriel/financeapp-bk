# Design

## Context

`RegisterUserUseCase` valida en este orden: primero que la moneda exista y después que el correo
esté libre (`existsByEmail`, sin distinguir mayúsculas). Recibe el correo normalizado con `trim` y
`toLowerCase` antes de cualquier consulta. El formato ya lo validó `RegisterUserRequest`, así que el
correo trae una sola `@`.

Hoy registran desde el API, contra la base local:

- once requests de `bruno/`, con correos `<prefijo>-<timestamp>@bruno.local`, uno nuevo por corrida;
- el front, con correos `*@front.local`.

Una lista de correos exactos no puede admitir correos con timestamp. El criterio de la tarea pide
que los dos sigan registrando, y también que Bruno pruebe el caso admitido y el no admitido, así que
en local la restricción tiene que estar encendida.

## Decisiones

### 1. Una fila admite un correo exacto o un dominio completo

```sql
CREATE TABLE finance.registration_allowlist (
    entry       VARCHAR(255) PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_registration_allowlist_entry CHECK (
        entry = lower(entry)
        AND entry ~ '^[^@[:space:]]*@[^@[:space:]]+\.[a-z]{2,}$')
);
```

Una entrada sin nada antes de la `@` (`@bruno.local`) admite el dominio completo, y una con
parte local (`ana@correo.com`) admite ese correo exacto. El CHECK obliga las minúsculas, así que
una inserción a mano con mayúsculas falla en vez de quedar sin efecto.

La consulta compara el correo normalizado con las dos formas:

```sql
SELECT EXISTS (
    SELECT 1 FROM finance.registration_allowlist
     WHERE entry IN (:email, '@' || split_part(:email, '@', 2))
)
```

El dominio se compara exacto, así que `@bruno.local` no admite `otro.bruno.local`. La clave
primaria sirve de índice.

- **Descartado: solo correos exactos, con la restricción apagada en local.** Bruno no podría probar
  el rechazo, que es lo único que cambia del comportamiento.
- **Descartado: los dominios de prueba en la configuración** (`registro.admitidos.dominios`).
  Tendría dos fuentes de verdad para la misma pregunta. Además `application-local.yaml` no se
  versiona, así que cada máquina tendría que acordarse de agregarlos.
- **Descartado: patrones `LIKE`.** El `_` es un comodín y es un carácter común en los correos:
  `ana_b@x.com` admitiría `anaxb@x.com`. Escaparlo a mano es un error esperando a pasar.
- **Descartado: correos fijos en Bruno.** Habría que reescribir once requests, y el alta seguiría sin
  ser idempotente.

El precio es que una fila `@gmail.com` en Neon abriría el registro a cualquier cuenta de Gmail. La
documentación de `docs/despliegue.md` lo advierte, y en producción solo se insertan correos exactos.

La tabla es propia y no una columna de `users`, porque guarda correos de personas que todavía no
existen como usuarios. Cuando llegue FA-104, puede ganar una columna con el plan del invitado.

### 2. El 403 lo lanza el caso de uso, con la excepción que ya existe

`BadRequestException` ya recibe el `HttpStatus`, y `WebExceptionHandler` responde con el que trae.
Por eso el rechazo es `new BadRequestException(FORBIDDEN, REGISTRATION_NOT_ALLOWED, …, "email")`, y
no hace falta ningún `case` nuevo en el handler.

El caso de uso queda con este orden: moneda, admitido y correo libre. Así cumple el orden del spec
sin mover la validación de formato, que sigue en el DTO.

- **Descartado: 409.** Ese código es para conflictos con el estado de un recurso. Aquí la petición
  es correcta y el servidor se niega a cumplirla: eso es un 403.
- **Descartado: comprobar la lista antes de la moneda.** El spec agrupa primero todos los 400, y el
  front no manda moneda: en la práctica el orden entre esas dos no se nota.

### 3. El interruptor vive en el caso de uso

`RegisterUserUseCase` recibe `@Value("${registro.admitidos.enabled}") boolean`. Si es `false`, no
llama al puerto. Pasa a tener un constructor explícito, como permite `AGENTS.md` para los beans con
`@Value` (es el patrón de `CheckSystemStatusUseCase`).

En `application.yaml`: `registro.admitidos.enabled: ${REGISTRO_ADMITIDOS_ENABLED:true}`. Lleva
default, a diferencia de los secretos, porque fallar cerrado es lo seguro: un servicio que no
declara la variable queda restringido y no deja de arrancar. El `application.yaml` de test lo repite
en `true`. `application-local.yaml` y `application-prod.yaml` no lo necesitan.

- **Descartado: un adapter que responda siempre `true` cuando está apagado.** Escondería una regla
  de negocio en la infraestructura, y la prueba unitaria del caso de uso dejaría de ver los dos
  caminos.

### 4. Los datos locales se admiten en los scripts que ya los limpian

- `test-data.sql` inserta `@bruno.local` e `invitado@financeapp.local`, y su borrado inicial agrega
  al usuario `invitado@financeapp.local`.
- `demo-data.sql` inserta `@front.local`.

Las dos inserciones usan `ON CONFLICT DO NOTHING`, porque `cargar-datos-local.sql` corre la demo dos
veces y la lista es compartida.

## Risks / Trade-offs

- [Producción queda con el registro cerrado para todos al aplicar el update] → Es lo que se busca.
  El paso de agregar los correos invitados está en `docs/despliegue.md`, junto al de aplicar el
  update. El dueño ya tiene cuenta, y el login no consulta la lista.
- [App nueva con la base vieja: el alta da 500 porque la tabla no existe] → El encabezado del
  update dice que va **antes** del despliegue. La app vieja con la base nueva funciona igual.
- [El 403 permite averiguar quién está invitado] → Hace falta la credencial Basic, y el dato es poco
  sensible mientras la app esté en pruebas. Queda en el Fuera de alcance de `proposal.md`.
- [`bruno/auth/` deja de ser repetible sin recargar los datos] → El request de correo exacto crea a
  `invitado@financeapp.local`, y la segunda corrida da 409. El `docs` de la carpeta lo dice, y
  `verificar-bruno.ps1 -RecargarDatos` recarga antes de cada corrida.

## Migration Plan

1. Merge a `dev`. No despliega nada.
2. Antes de promover a `main`: aplicar el update en Neon con el procedimiento de
   `docs/despliegue.md` y anotarlo en el registro de updates aplicados.
3. Insertar en Neon los correos invitados.
4. Promover a `main`. Cloud Run no necesita variables nuevas.

Para revertir hay que hacer las dos cosas: revertir el commit y borrar la tabla con el bloque de
reversión del update. O bien apagar la restricción con `REGISTRO_ADMITIDOS_ENABLED=false` en el
servicio, sin desplegar.
