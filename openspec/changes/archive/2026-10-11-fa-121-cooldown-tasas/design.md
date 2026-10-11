# Design

## Context

`ResolveExchangeRateUseCase.guardarDelProveedor` es el único punto que llama al proveedor. Lo
alcanzan `resolve` (el endpoint y la conversión de cada movimiento) y `refreshToday` (el alta en
lote, antes de convertir). Hoy decide si llamar solo mirando la base: hay fila de hoy o no la hay.
La decisión 3 de FA-120 dejó escrito que la salida era «recordar en memoria el último intento del
día». Este change la implementa y reemplaza esa parte de la decisión.

## Goals / Non-Goals

**Goals:**
- Que el número de llamadas diarias al proveedor quede acotado sin importar el tráfico: una si
  responde, y como mucho una por intervalo si falla.

**Non-Goals:**
- Persistir el registro entre reinicios o compartirlo entre instancias (ver Riesgos).

## Decisions

### 1. El registro vive en memoria, en una clase propia de `application/usecase`

Una clase nueva, `ConsultasAlProveedor` (`@Component`), guarda en un `AtomicReference` el día de la
última consulta exitosa y el instante del último fallo, y expone tres operaciones: si se puede
consultar ahora, anotar un éxito y anotar un fallo. Recibe el `Clock` de la app y el intervalo con
`@Value("${tasas.reintento-tras-fallo}")`, así que lleva constructor explícito, como permite
`AGENTS.md` para los beans con `@Value`. `ResolveExchangeRateUseCase` la recibe como una
dependencia más y conserva `@AllArgsConstructor`.

Separada del caso de uso porque el tercer criterio de la tarea pide probar el intervalo con un
`Clock` fijo: con la regla en su propia clase, el test mueve el reloj y comprueba las tres
operaciones sin mocks de puertos. El caso de uso solo se prueba en que la consulte y le informe.

**Descartado:**
- **Guardar el intento en la base** (una tabla o una fila centinela en `exchange_rates`).
  Sobreviviría a reinicios y serviría con varias instancias, pero agrega un update de esquema, otra
  consulta por resolución y escrituras en un camino que hoy solo lee. Con una instancia (FA-48), no
  compensa.
- **Un decorador del `ExchangeRateProviderPort` en infraestructura.** Dejaría intacto el caso de
  uso, pero el cableado ya elige entre el adapter real y el stub por propiedad y perfil, y un tercer
  bean del mismo puerto obligaría a `@Qualifier` en los tres. Además, la regla depende del día de la
  app, que es lógica de aplicación, no del adapter.
- **`Mono.cache(Duration)` sobre la respuesta del proveedor.** Cachea el valor, no el fallo, y un
  TTL fijo no corta en el cambio de día.

### 2. Qué cuenta como éxito y qué como fallo

Éxito es que el proveedor respondió, el adapter aceptó la respuesta y sus filas quedaron guardadas,
aunque no traiga ninguna moneda activa: el proveedor contestó, y volver a preguntarle ese día daría
lo mismo. Fallo es cualquier error que salga del puerto, que ya incluye el timeout y la respuesta
inválida (decisión 5 de FA-120). Un error al **guardar** las filas no se anota ni como éxito ni como
fallo: sigue propagándose como hoy, y la siguiente resolución vuelve a consultar. Anotar el éxito al
recibir la respuesta dejaría el día sin filas y sin poder reponerlas.

El éxito se anota con el día del `Clock`, y la comprobación compara con el día actual, así que en
el cambio de día, según `app.timezone`, se vuelve a consultar. El fallo se anota con el instante
del `Clock` y se compara con el intervalo. Un fallo a las 23:50 con intervalo de 1 hora bloquea
también hasta las 00:50 del día siguiente: es el mismo proveedor caído.

### 3. Intervalo por defecto de 1 hora, configurable

`tasas.reintento-tras-fallo: ${TASAS_REINTENTO_TRAS_FALLO:1h}` en `application.yaml`, enlazado como
`Duration`. Una hora deja como máximo 24 llamadas al día con el proveedor caído, frente a una por
request hoy, y recupera el dato del día en una hora si el fallo fue pasajero. No es un secreto, así
que lleva default, como `tasas.exchangerate-api.timeout`. El yaml de test necesita la clave, porque
reemplaza al de `main`.

## Risks / Trade-offs

- [Un reinicio borra el registro] → La primera resolución tras arrancar vuelve a consultar. Con
  Cloud Run escalando a 0, eso puede pasar varias veces al día. Sigue acotado por arranques, no por
  requests.
- [Varias instancias no comparten el registro] → Hoy el máximo es 1 instancia (FA-48). Si sube, se
  rehace esta cuenta junto con la del pool, y la salida es la tabla descartada en la decisión 1.
- [Recargar `test-data.sql` con la app corriendo después de una consulta exitosa] → `test-data.sql`
  borra `exchange_rates`. Si la app ya consultó ese día, no repone las filas hasta el día siguiente o
  el próximo reinicio. `verificar-bruno.ps1` levanta una app nueva en cada corrida, así que la
  colección no lo nota. A mano, reiniciar la app tras recargar.
- [Dos requests simultáneos en la primera consulta del día] → Los dos pueden llamar antes de que
  el primero anote el éxito. Queda como hoy: `ON CONFLICT DO NOTHING` hace inofensiva la segunda
  escritura, y es una llamada extra, no una por request.
