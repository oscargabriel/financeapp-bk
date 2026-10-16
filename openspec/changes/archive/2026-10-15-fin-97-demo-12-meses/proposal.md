# Proposal

FIN-97, epic FIN-74 (Calidad y mantenimiento). Refinada desde la raíz el 15-10-2026, junto con
FIN-30, FIN-95 y FIN-96, que ya están entregadas.

## Why

La demo (`demo-data.sql`) cubre seis meses. El front no tiene con qué probar listas largas ni
reportes con un año de historia: la serie mensual de gastos se corta a la mitad, y lo compartido y
las cuotas solo existen en los últimos dos meses. Medida en la base local el 15-10-2026, la demo trae
entre 25 y 37 movimientos en cada mes completo, menos el mes de pocos movimientos, que trae 6. El
«unos 18 por mes» del issue es de antes de FIN-30, FIN-95 y FIN-96.

Decisión del epic que aplica:

- La demo (`demo-data.sql`) cubre personas afiliadas y lo compartido, pendientes del asistente,
  movimientos en USD y 12 meses con unos 30 movimientos por mes (15-10-2026). Este change es la
  parte de los 12 meses, y va después de las otras tres.

## What Changes

- **Doce meses**, del mes en curso a once meses atrás, con unos 30 movimientos en cada mes completo.
  El mes 3 sigue siendo el de pocos movimientos, sin fijos ni metas: es el caso del mes casi vacío.
  El mes en curso solo trae lo que ya ocurrió, como hoy.
- **Montos que varían entre meses**: los gastos de rutina (mercado semanal, almuerzos, transporte,
  café, gasolina) salen de una plantilla con una variación fija por mes y por fila, no aleatoria, para
  que dos cargas dejen lo mismo. Los meses 8 a 11 tienen el salario y el arriendo de antes del
  aumento anual. Los gastos extraordinarios siguen escritos a mano, distintos en cada mes.
- **Repartido en el rango nuevo**:
  - Los fijos, las transferencias fijas y la comisión de la cuenta en dólares, en los doce meses
    menos el 3.
  - Metas globales en los doce meses menos el 3. Las de categoría siguen en los dos últimos meses,
    donde ya están los casos de tope pasado y sin pasar.
  - La serie del almacenamiento en la nube empieza hace once meses. El club de lectura sigue siendo
    la serie reciente, de hace tres.
  - Una tercera compra en cuotas, ya pagada del todo: una nevera de hace diez meses en la Mastercard.
  - Ingresos y gastos en USD en los meses viejos, y una compra de dólares más.
  - Lo compartido gana dos grupos viejos, ya saldados, y las personas afiliadas se crean y se afilian
    antes de ellos. El usuario se crea antes del primer movimiento.
  - Los pendientes del asistente y lo compartido pendiente siguen siendo recientes.
- **Tasa de respaldo** fechada el día 1 de hace doce meses, antes del movimiento más antiguo
  (FIN-45).
- El encabezado de `demo-data.sql` dice el rango y el volumen nuevos, y lo mismo `AGENTS.md`
  (*Base de datos*) y `docs/despliegue.md` donde hablan de los seis meses.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: cambia un script de datos, no el comportamiento del sistema (`skip_specs: true`). Las filas
siguen lo que ya describen `transacciones`, `recurrentes`, `cuotas`, `compartidos` y `afiliaciones`.

## Fuera de alcance

- Cambios en `test-data.sql`, en `bruno/`, en el esquema o en el API.
- Series terminadas o pausadas, compras en cuotas canceladas y otras monedas: el issue pide repartir
  lo que hay, no casos nuevos.
- Que las fechas de la demo avancen solas: se siguen calculando al cargar.
- Más pendientes del asistente o más compartidos pendientes: son recientes por naturaleza, y sus
  números los fijan FIN-30 y FIN-95.

## Impact

- `docs/database/demo-data.sql`, `AGENTS.md` (un párrafo de *Base de datos*), `docs/despliegue.md`.
- Sin cambios en `src/`, `bruno/`, el esquema ni el contrato del API. El saldo de cada cuenta de
  `demo@` y `dev@` cambia. En Neon, la próxima recarga de la demo trae los doce meses.
