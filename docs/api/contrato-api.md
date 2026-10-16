# Contrato del API — financeapp-bk

Este documento es la referencia para consumir el API: qué endpoints hay, cómo se autentica cada uno,
qué reciben y qué devuelven, y qué hay que tener en cuenta del lado del cliente. Describe el
comportamiento del código en `dev`.

El contrato está partido en un archivo por recurso, nombrado como la capacidad de `openspec/specs/`
que lo describe, y este índice solo dice cuál abrir. Si un endpoint cambia, se actualiza el archivo
de su recurso en el mismo PR; aquí, solo su fila de la tabla.

## Índice

| Método | Ruta | Autenticación | Para qué | Archivo |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Basic | Crear un usuario | [auth.md](auth.md) |
| `POST` | `/api/auth/login` | Basic | Abrir una sesión: access y refresh | [auth.md](auth.md) |
| `POST` | `/api/auth/google/register` | Basic | Crear un usuario con su cuenta de Google y abrirle la sesión | [auth.md](auth.md) |
| `POST` | `/api/auth/google/login` | Basic | Abrir una sesión con la cuenta de Google | [auth.md](auth.md) |
| `POST` | `/api/auth/refresh` | Basic | Cambiar un refresh vigente por un access y un refresh nuevos | [auth.md](auth.md) |
| `POST` | `/api/auth/logout` | Basic | Cerrar la sesión de un refresh | [auth.md](auth.md) |
| `GET` | `/api/status` | Basic | Estado del servicio y de la base | [auth.md](auth.md) |
| `GET` | `/api/users/me` | Bearer | Perfil del usuario del token | [usuarios.md](usuarios.md) |
| `PATCH` | `/api/users/me` | Bearer | Modificar el perfil | [usuarios.md](usuarios.md) |
| `PUT` | `/api/users/me/password` | Bearer | Cambiar la contraseña | [usuarios.md](usuarios.md) |
| `POST` | `/api/users/me/password` | Bearer | Definir la primera contraseña, confirmando con Google | [usuarios.md](usuarios.md) |
| `GET` | `/api/accounts` | Bearer | Listar las cuentas del usuario | [cuentas.md](cuentas.md) |
| `POST` | `/api/accounts` | Bearer | Crear una cuenta | [cuentas.md](cuentas.md) |
| `PATCH` | `/api/accounts/{id}` | Bearer | Modificar una cuenta | [cuentas.md](cuentas.md) |
| `DELETE` | `/api/accounts/{id}` | Bearer | Borrar una cuenta | [cuentas.md](cuentas.md) |
| `GET` | `/api/categories` | Bearer | Listar las categorías del usuario | [categorias.md](categorias.md) |
| `POST` | `/api/categories` | Bearer | Crear una categoría | [categorias.md](categorias.md) |
| `PATCH` | `/api/categories/{id}` | Bearer | Modificar una categoría | [categorias.md](categorias.md) |
| `DELETE` | `/api/categories/{id}` | Bearer | Borrar una categoría | [categorias.md](categorias.md) |
| `GET` | `/api/catalogs/account-types` | Bearer | Tipos de cuenta válidos | [catalogos.md](catalogos.md) |
| `GET` | `/api/catalogs/transaction-types` | Bearer | Tipos de movimiento válidos | [catalogos.md](catalogos.md) |
| `GET` | `/api/catalogs/currencies` | Bearer | Monedas activas | [catalogos.md](catalogos.md) |
| `GET` | `/api/catalogs/categories` | Bearer | Categorías del usuario para elegir `categoryId` | [catalogos.md](catalogos.md) |
| `GET` | `/api/exchange-rates` | Bearer | Tasa de cambio de un par de monedas en una fecha | [tasas-de-cambio.md](tasas-de-cambio.md) |
| `POST` | `/api/transactions` | Bearer | Registrar un lote de movimientos, compartidos o no con afiliados | [transacciones.md](transacciones.md) |
| `PATCH` | `/api/transactions/{id}` | Bearer | Modificar un movimiento | [transacciones.md](transacciones.md) |
| `DELETE` | `/api/transactions/{id}` | Bearer | Eliminar un movimiento | [transacciones.md](transacciones.md) |
| `GET` | `/api/transactions/pending` | Bearer | Movimientos pendientes de aprobación, o solo los que compartió una persona | [transacciones.md](transacciones.md) |
| `POST` | `/api/transactions/{id}/approve` | Bearer | Aprobar un pendiente; con cuenta y categoría si llegó compartido | [transacciones.md](transacciones.md) |
| `POST` | `/api/transactions/{id}/reject` | Bearer | Rechazar un pendiente | [transacciones.md](transacciones.md) |
| `POST` | `/api/transactions/pending/reject` | Bearer | Rechazar varios pendientes, de cualquier grupo, en una petición | [transacciones.md](transacciones.md) |
| `POST` | `/api/transactions/groups/{groupId}/approve` | Bearer | Confirmar juntos los pendientes de un grupo | [transacciones.md](transacciones.md) |
| `POST` | `/api/transactions/groups/{groupId}/reject` | Bearer | Descartar juntos los pendientes de un grupo | [transacciones.md](transacciones.md) |
| `POST` | `/api/recurrences` | Bearer | Crear una serie de gastos o ingresos que se repiten | [recurrentes.md](recurrentes.md) |
| `GET` | `/api/recurrences` | Bearer | Series activas, con su próxima ocurrencia | [recurrentes.md](recurrentes.md) |
| `PATCH` | `/api/recurrences/{id}` | Bearer | Modificar una serie, en sus futuras o en todas | [recurrentes.md](recurrentes.md) |
| `DELETE` | `/api/recurrences/{id}` | Bearer | Cancelar una serie | [recurrentes.md](recurrentes.md) |
| `POST` | `/api/installment-purchases/preview` | Bearer | Simular las cuotas de una compra con tarjeta | [cuotas.md](cuotas.md) |
| `POST` | `/api/installment-purchases` | Bearer | Registrar una compra con tarjeta en cuotas | [cuotas.md](cuotas.md) |
| `GET` | `/api/installment-purchases` | Bearer | Compras con cuotas por venir | [cuotas.md](cuotas.md) |
| `PATCH` | `/api/installment-purchases/{id}` | Bearer | Modificar una compra, en sus cuotas futuras o en todas | [cuotas.md](cuotas.md) |
| `DELETE` | `/api/installment-purchases/{id}` | Bearer | Cancelar las cuotas que faltan | [cuotas.md](cuotas.md) |
| `GET` | `/api/monthly-spending` | Bearer | Gasto e ingreso mensual, y el gasto contra la meta | [reportes.md](reportes.md) |
| `GET` | `/api/reports/transactions` | Bearer | Movimientos y totales de un rango de días | [reportes.md](reportes.md) |
| `GET` | `/api/reports/balance` | Bearer | Ingresos menos gastos de un rango y de siempre, el saldo débito total en la moneda base y las cuentas | [reportes.md](reportes.md) |
| `GET` | `/api/reports/home` | Bearer | Lo que muestra la pantalla de Inicio, ya calculado: serie de 6 meses, variación del gasto, últimos y más costosos, saldo débito y cupo usado | [reportes.md](reportes.md) |
| `POST` | `/api/assistant/messages` | Bearer | Registrar o consultar con un mensaje en lenguaje natural | [asistente.md](asistente.md) |
| `GET` | `/api/affiliations` | Bearer | Solicitudes de afiliación abiertas y afiliaciones aceptadas | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/affiliations` | Bearer | Enviar una solicitud de afiliación a un correo | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/affiliations/{id}/accept` | Bearer | Aceptar una solicitud recibida | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/affiliations/{id}/reject` | Bearer | Rechazar una solicitud recibida | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/affiliations/{id}/cancel` | Bearer | Cancelar una solicitud enviada | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/affiliations/{id}/unlink` | Bearer | Desvincular una afiliación aceptada | [afiliaciones.md](afiliaciones.md) |
| `GET` | `/api/affiliations/balances` | Bearer | Lo que el usuario y cada persona se deben, por moneda | [afiliaciones.md](afiliaciones.md) |
| `GET` | `/api/affiliations/{id}/shares` | Bearer | Las partes compartidas entre el usuario y esa persona | [afiliaciones.md](afiliaciones.md) |
| `POST` | `/api/telegram/webhook` | Secreto del webhook | Recibir los updates del bot de Telegram y vincular el chat con el contacto propio (lo llama Telegram) | [telegram.md](telegram.md) |

## Archivos

| Archivo | Qué cubre |
|---|---|
| [generalidades.md](generalidades.md) | Lo común a todos los endpoints: URL base, formato, montos y fechas; autenticación Basic, Bearer y el secreto del webhook, y el 401; errores y el saldo no negativo; CORS, y lo que el API todavía no tiene |
| [auth.md](auth.md) | Registro, login, Google, refresh y logout (`/api/auth/*`), y `GET /api/status` |
| [usuarios.md](usuarios.md) | El perfil y la contraseña del usuario (`/api/users/me`) |
| [cuentas.md](cuentas.md) | Las cuentas (`/api/accounts`) |
| [categorias.md](categorias.md) | Las categorías (`/api/categories`) |
| [catalogos.md](catalogos.md) | Los catálogos (`/api/catalogs/*`) |
| [tasas-de-cambio.md](tasas-de-cambio.md) | La tasa de cambio de un par de monedas (`/api/exchange-rates`) |
| [transacciones.md](transacciones.md) | Alta en lote, edición y borrado de movimientos (`/api/transactions`), los pendientes de aprobación y los programados |
| [compartidos.md](compartidos.md) | Gastos y transferencias compartidos con afiliados: cómo se piden en el alta de movimientos y cómo salen en la respuesta |
| [recurrentes.md](recurrentes.md) | Las series recurrentes (`/api/recurrences`) |
| [cuotas.md](cuotas.md) | Las compras con tarjeta en cuotas (`/api/installment-purchases`) |
| [reportes.md](reportes.md) | El gasto mensual (`/api/monthly-spending`), los reportes de movimientos y de balance, y el resumen de Inicio (`/api/reports/*`) |
| [asistente.md](asistente.md) | El asistente en lenguaje natural (`/api/assistant/messages`) |
| [afiliaciones.md](afiliaciones.md) | Las afiliaciones entre usuarios y lo que se deben (`/api/affiliations`) |
| [telegram.md](telegram.md) | El webhook del bot de Telegram (`/api/telegram/webhook`) |
