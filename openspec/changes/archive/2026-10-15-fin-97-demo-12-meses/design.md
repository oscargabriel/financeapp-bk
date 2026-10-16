# Design

## Context

`demo-data.sql` arma cada mes con tres bloques que se cruzan con una lista de meses
`(0), (1), (2), (4), (5)`: los fijos (salario, arriendo, servicios, suscripciones), las
transferencias fijas y la comisión de la cuenta en dólares. Lo demás está escrito a mano, fila por
fila y mes por mes: unas 60 filas de gastos e ingresos en COP y 11 en USD. Entre esas filas a mano,
la mitad son la rutina del mes repetida con otro monto: cuatro mercados semanales, almuerzos, taxi,
bus, café, gasolina.

Las series, las compras en cuotas y lo compartido no dependen de la lista de meses: cada uno lleva su
propio «hace N meses» o «hace N días».

Llevar el script a doce meses con el mismo estilo son unas 70 filas a mano más, casi todas de rutina.

## Goals / Non-Goals

**Goals:**

- Doce meses con unos 30 movimientos en cada mes completo, salvo el mes 3, y montos que cambian
  entre meses.
- Que dos cargas el mismo día dejen el mismo estado, y que los saldos se vean como los de una
  persona real a lo largo del año.

**Non-Goals:**

- Cambiar la forma de las filas: siguen siendo las que dejaría el API, como hoy.
- Casos nuevos (series terminadas, cuotas canceladas, otras monedas): ver *Fuera de alcance* en
  `proposal.md`.

## Decisions

### La rutina sale de una plantilla con variación fija

Los diez gastos de rutina de cada mes se escriben una sola vez, con su monto base, y se cruzan con
los meses. El monto de cada mes es

```
base × (1 + ((mes × 7 + fila × 5) % 13 − 6) / 100)
```

redondeado a centavos en el mercado y a cientos de pesos en los demás: entre −6 % y +6 %, distinto
en cada mes y en cada fila. Lo mismo se aplica a «Luz, agua y gas», que hoy crece en línea recta con
el mes y en doce meses quedaría un 25 % más cara en el mes más viejo.

| Fila | Cuenta | Categoría | Base | Día y hora |
|---|---|---|---|---|
| Mercado semanal ×4 | tarjeta | Mercado | 190.000,00 | 2, 9, 16 y 23, 11 h |
| Almuerzo ×2 | tarjeta | Restaurantes | 40.000 | 4 y 18, 13 h, Telegram |
| Taxi | efectivo | Transporte | 21.000 | 11, 22 h, Telegram |
| Bus | efectivo | Transporte | 9.800 | 20, 7 h, Telegram |
| Café | efectivo | Restaurantes | 9.500 | 26, 8 h, Telegram |
| Gasolina | tarjeta | Transporte | 125.000 | 21, 8 h |

«Luz, agua y gas» se redondea a centavos, como el mercado. Las filas a mano de los meses 0 a 5 que
son rutina se borran; las extraordinarias se quedan como están. El «Almuerzo con el equipo» del mes
en curso pasa del día 4 al 3, para no coincidir con el almuerzo de la rutina.

Descartado:

- **Escribir todo a mano.** Unas 70 filas más, casi todas de rutina, y cada ajuste futuro de montos
  hay que hacerlo en doce lugares.
- **`random()`.** Rompe el criterio de que dos cargas el mismo día dejen el mismo estado, y un
  descuadre en un reporte no se podría reproducir.

### El mes 3 sigue siendo el de pocos movimientos

El criterio pide unos 30 movimientos por mes, pero el mes 3 existe a propósito: es el único sin
fijos, sin transferencias, sin metas y con tres gastos. El front lo usa para ver un mes casi vacío y
uno sin meta. Se queda como está, con el salario del mes y el arriendo; la rutina tampoco entra ahí.

Descartado: llenarlo como los demás. Con doce meses llenos, la demo se queda sin ese caso.

### Aumento anual en el mes 8

En los meses 8 a 11 el salario va a 2.450.000 por quincena y el arriendo a 1.560.000. Del mes 7 al
0 siguen los de hoy, 2.600.000 y 1.650.000. Es la forma en que el salario y el arriendo de una
persona cambian en un año, y deja un escalón visible en la serie mensual sin tocar los meses
recientes, que el front ya conoce. La prima de servicios del mes 5 (2.600.000) se repite en el mes
11 con el salario viejo (2.450.000): las dos primas del año.

### Lo que se reparte, y cómo

- **Fijos, transferencias fijas y comisión en USD**: la lista de meses pasa a `0..11` sin el 3. El
  retiro de cajero baja de 300.000 a 150.000: con doce meses, el efectivo acumularía más de tres
  millones, porque la rutina en efectivo gasta unos 40.000 al mes.
- **Metas**: globales en los once meses con fijos, de 4.000.000 en los meses 6 y 7 y de 3.800.000 en
  los meses 8 a 11, con el salario viejo. Las de categoría se quedan en los meses 0 y 1. Con la
  plantilla, el mercado del mes 1 suma al menos 4 × 178.600 = 714.400, así que el tope de 700.000 se
  sigue pasando siempre.
- **Series**: la nube empieza el 12 de hace once meses. El club de lectura sigue en hace tres meses:
  la demo conserva una serie larga y una reciente.
- **Cuotas**: una nevera de 1.800.000 a 6 cuotas en la Mastercard, del 8 de hace diez meses, en
  Vivienda. Con corte 20 y pago 5, sus cuotas vencen el 5 de los meses 9 a 4: es la compra ya
  pagada del todo. La consulta de las cuotas pasa a llevar la categoría por compra.
- **USD**: pagos de cliente del exterior en los meses 6 (600), 8 (680) y 10 (620), un curso en línea
  en el 7 (49,00) y una licencia de software en el 9 (119,00). Una compra de dólares más, de
  1.000.000 COP, en el mes 9.
- **Lo compartido**: dos grupos viejos, ya saldados.
  - G7, hace 240 días: el usuario paga el hostal en la playa (450.000, tarjeta, Viajes). Carlos y
    Valentina pagan su parte de 150.000; los dos ingresos del usuario, aprobados en la nómina como
    Reembolsos.
  - G8, hace 150 días: Carlos paga el mercado de la finca (210.000, Mercado). El usuario paga su
    parte de 105.000 desde la nómina, y el ingreso de Carlos está aprobado.

  Carlos se crea hace 330 días y se afilia hace 320; Valentina, hace 300 y 295. El orden entre los
  dos no cambia, y lo pendiente de FIN-30 tampoco.
- **Usuario**: se crea el día 1 de hace doce meses, antes del primer movimiento. Hoy se crea con
  `now()`, aunque tenga movimientos de hace seis meses.
- **Tasa de respaldo**: el día 1 de hace doce meses, un mes antes del mes más viejo, como hoy lo está
  respecto del sexto.

### Extraordinarios de los meses 6 a 11

| Mes | Movimientos |
|---|---|
| 6 | Consulta médica 85.000 (nómina), Camisa 129.900, Bolos 76.000, Ilustraciones 1.400.000 (ingreso) |
| 7 | Concentrado del perro 168.500, Regalo de aniversario 260.000, Cena de aniversario 198.000, Curso de fotografía 520.000 (nómina) |
| 8 | Tiquetes a Medellín 486.000, Hotel en Medellín 610.000, Propina 15.000 (efectivo), Sitio web de la panadería 1.900.000 (ingreso) |
| 9 | Impuesto de renta 1.150.000 (nómina), Lentes 450.000, Mouse y teclado 189.900, Venta de la bicicleta 700.000 (ingreso) |
| 10 | Reparación del calentador 230.000 (nómina), Festival de música 410.000, Tenis 279.900, Reembolso de la aseguradora 145.000 (ingreso) |
| 11 | Prima de servicios 2.450.000 (ingreso), Regalo para mamá 180.000, Cena familiar 245.000, Odontología 160.000 (nómina) |

Sin cuenta indicada, los gastos van a la tarjeta y los ingresos a la nómina. Con los fijos (8), la
rutina (10), las transferencias fijas (3), la comisión y la nube, cada mes viejo tiene 23 movimientos
más estos 4, más los USD y las cuotas que le toquen: entre 27 y 31. Los meses 1 a 5 quedan parecidos
a hoy.

**El mes 1 es la excepción del rango** (decisión del usuario en el apply, 15-10-2026): queda con 40
movimientos, contra 37 antes del cambio, porque ahí se juntan los casos recientes de FIN-30, FIN-95 y
FIN-96 (lo compartido, los USD, las cuotas) y la plantilla le suma el bus, el café y un taxi. Se
acepta en vez de recortar sus extraordinarios.

### Invariantes de saldo, comprobadas por consulta

Con doce meses, un pago de tarjeta fijo puede dejarla con saldo a favor en los meses flojos o pasada
del cupo después del portátil. Al cargar se comprueba, acumulando por fecha los movimientos
confirmados de cada cuenta desde su saldo inicial:

- ninguna cuenta de efectivo, débito o ahorros baja de cero;
- las tarjetas no pasan del cupo ni quedan a favor (el saldo de una tarjeta es negativo cuando
  debe);
- la cuenta en dólares no baja de 1.000 USD, como en FIN-96.

Si una falla, se ajusta el monto del pago de tarjeta o del extraordinario que la rompe, y el ajuste
se anota aquí.

Ajuste hecho: con el pago fijo de 1.600.000, la Mastercard quedaba a favor hasta +1,29 M en los meses
viejos y llegaba a −11,86 M, pasada del cupo de 8 M, en los recientes. **El pago de la tarjeta paga
lo gastado desde el pago anterior**: el 5 de cada mes menos el 3, desde la nómina, por la suma de
los gastos confirmados de la tarjeta entre los dos pagos. Va en un INSERT propio después de todos los
gastos de la tarjeta (cuotas, series, lo compartido). Tras cada pago la tarjeta queda en cero, y su
deuda máxima es −6,46 M, en el mes del portátil. Con eso, las tres invariantes se cumplen: la nómina
no baja de 3,4 M, el efectivo de 460.000 y la cuenta en dólares de 1.483 USD.

## Risks / Trade-offs

- **Cambian los montos de los meses recientes.** El mercado, los almuerzos y el transporte de los
  meses 0 a 5 salen de la plantilla. Ninguna spec ni assert fija esos montos, y el front solo lee,
  así que el riesgo es que alguien recuerde un número viejo de la demo, no que algo se rompa.
- **La plantilla es menos legible que una fila por movimiento.** Se compensa con el resumen por mes
  que el script ya imprime al final.
- **Lo compartido sigue copiando lo que escribe el API** (FIN-30): los grupos nuevos tienen el mismo
  riesgo de quedar viejos si cambia cómo se escriben las partes.
