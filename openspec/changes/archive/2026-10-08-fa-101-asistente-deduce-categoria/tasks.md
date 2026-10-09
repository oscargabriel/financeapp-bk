# Tasks

## 1. Lo que se envía al modelo

Skills: `java-architect`.

- [x] 1.1 RED: en `GeminiAssistantAdapterTest.mandaLasReglasConLaFechaYLosNombresLasTresFuncionesYElModoAuto`
  (o un test nuevo junto a él) comprobar que la instrucción pide deducir la categoría cuando el
  usuario no la nombra, omitirla si no hay una sola clara, y enviar la cuenta vacía si no la nombra;
  y que la descripción de `categoria` en `crear_movimiento` pide lo mismo. Verificar que falla por
  el texto ausente.
- [x] 1.2 GREEN: reescribir la regla 3 de `instrucciones.txt` y la descripción de `categoria` en
  `funciones.json`. Verificar con `GeminiAssistantAdapterTest` en verde.

## 2. Gasto sin categoría en Bruno

Skills: `bruno-cli`.

- [x] 2.1 `bruno/assistant/gasto-sin-categoria.yml` con seq 19: el stub llama `crear_movimiento`
  `EXPENSE` sin `categoria` y la respuesta es 200 `NEEDS_CLARIFICATION`, el `message` nombra «Café»
  y `transaction` es `null`. Correr `seq` de los siguientes una posición y actualizar la
  documentación de `pendientes-sin-las-aclaraciones.yml` (ahora son cuatro aclaraciones). Verificar
  con `verificar-bruno.ps1`.
- [x] 2.2 `bruno-personal/asistente/mensaje.yml`: la documentación dice que la categoría se deduce
  si no se nombra y la cuenta no. No se ejecuta: apunta a Neon.

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura.
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` (con `-Puerto`
  si el 8080 está ocupado) en verde, con los conteos reales.
- [x] 3.3 Comprobación de humo contra Gemini real, con la key y el modelo de `application-local.yaml`:
  enviar como `dev@financeapp.local` «gaste 20 mil en el almuerzo con la <cuenta de dev@>» y anotar
  aquí el `intent` y la categoría. Lo esperado es `CREATE_TRANSACTION` con Restaurantes. Rechazar
  después ese pendiente para no dejarlo en los datos de dev@.

## Notas de implementación

- **Suite:** `gradlew build` con 739 tests, 0 fallos y 0 omitidos; cobertura de línea 98,31 %.
- **Bruno:** `verificar-bruno.ps1 -RecargarDatos` con 313/313 requests, 255/255 tests y 702/702
  aserciones, incluido `bruno/assistant/gasto-sin-categoria.yml`.
- **Humo contra Gemini real (3.3)**, con `gemini-3.5-flash-lite`, como `dev@financeapp.local`:
  - «gaste 20 mil en el almuerzo con la mastercard oro» dio `CREATE_TRANSACTION`, gasto de 20.000
    COP en Mastercard Oro con la categoría Restaurantes, `PENDING` y `TELEGRAM`. El pendiente se
    rechazó después.
  - «gaste 20 mil en el almuerzo», sin cuenta, dio `NEEDS_CLARIFICATION` con «Falta la cuenta.» y la
    lista de cuentas: el modelo no la dedujo.
- **La key de `application-local.yaml` era de relleno** (Google respondía 400 «API key not valid»,
  que la app devuelve como 502). La prueba se hizo con la key de `application-prod.yaml`, pasada en
  la variable `ASISTENTE_GEMINI_API_KEY` solo a esa corrida, sin tocar los yaml.
- El test de la instrucción busca frases cortas: las largas quedaban partidas por los saltos de
  línea de `instrucciones.txt` y el test fallaba aunque la regla estuviera.
