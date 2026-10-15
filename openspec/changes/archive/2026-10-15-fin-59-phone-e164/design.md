# Design

## Context

El formato de `phone` lo decide una sola expresión, `Formatos.CELULAR`, que comparten el alta y el
parche del perfil con el mismo mensaje (`Formatos.MENSAJE_CELULAR`). El caso de uso solo recorta y
convierte el blanco en `null`. La columna `users.phone` es `VARCHAR(20)` sin `CHECK`.

## Goals / Non-Goals

**Goals:**
- Que lo guardado en `users.phone` por el API sea comparable tal cual con el número que entrega
  Telegram (FIN-60), sin normalizar al comparar.

**Non-Goals:**
- Validar que el número exista o sea de un país concreto.

## Decisions

### Una expresión regular, no libphonenumber

`^\s*(\+[1-9][0-9]{6,14})?\s*$`: el `+` obligatorio, el indicativo sin cero inicial (ningún código
de país E.164 empieza por 0) y de 7 a 15 dígitos en total. Sigue aceptando el vacío y los espacios
del borde, como todas las de `Formatos`, para que el `@NotBlank` o el blanco que borra en el parche
sigan funcionando igual.

Descartado: la librería libphonenumber de Google, que valida longitud y prefijos por país. Agrega
una dependencia de varios MB para una regla que el criterio no pide, y rechazaría números que
Telegram sí entrega (rangos nuevos que la versión de la librería aún no conoce).

El mínimo se queda en 7 dígitos, el de hoy: con el indicativo incluido es holgado, pero subirlo no
protege nada que FIN-60 necesite y rechazaría números cortos reales de países pequeños.

### Rechazar los separadores en vez de normalizarlos

Un `+57 300 123 4567` es 400, no se limpia. Lo dice el criterio de FIN-59, y mantiene la regla del
API actual: el front manda el número limpio y el back no inventa la forma en que se guarda. La
normalización, si se quiere, es del formulario (FIN-62).

### Sin `CHECK` en la base

La regla vive en el request, como la de los demás formatos que no tienen contraparte en el
esquema. Un `CHECK` exigiría un `update/` y, antes, limpiar los celulares viejos de Neon (el de la
demo, `3109876543`), que es justo la migración que el epic dejó fuera. Si FIN-60 necesita
garantizarlo en la base, lo agrega allí.

## Risks / Trade-offs

- [Un usuario de Neon con el celular viejo no se vincula en FIN-60] → Mientras la app está en
  prueba solo existe la demo; se corrige recargando `demo-data.sql` o editando el perfil.
- [Un cliente que mande el celular sin `+` empieza a recibir 400] → El único cliente es el front,
  que lo adapta en FIN-62 con el mensaje que publica el contrato.
