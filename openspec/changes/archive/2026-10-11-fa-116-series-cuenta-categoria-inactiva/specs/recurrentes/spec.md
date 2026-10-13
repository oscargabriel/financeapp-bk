# Spec Delta

## ADDED Requirements

### Requirement: La puesta al día respeta la cuenta y la categoría
La puesta al día de las series sin fin MUST NOT crear ocurrencias de una serie cuya cuenta está
desactivada o borrada, o cuya categoría está borrada. La serie SHALL seguir sin cancelar. Si su
cuenta se reactiva, la siguiente puesta al día SHALL crear las ocurrencias que falten desde su
contador, como con una serie atrasada. Las reglas de cuentas y categorías impiden llegar a ese estado
desde el API; el requisito cubre los datos anteriores a ellas.

#### Scenario: Serie atrasada sobre una cuenta desactivada
- **WHEN** una serie semanal sin fin tiene creada solo una ocurrencia de hace cuatro semanas, sobre una cuenta desactivada, y el usuario pide `GET /api/accounts`
- **THEN** la serie sigue con una sola ocurrencia, y el `currentBalance` de la cuenta no cambia

#### Scenario: Serie atrasada sobre una cuenta borrada
- **WHEN** la misma situación se da sobre una cuenta borrada
- **THEN** la serie sigue con una sola ocurrencia

#### Scenario: Serie atrasada con la categoría borrada
- **WHEN** la misma situación se da sobre una cuenta activa, con la categoría de la serie borrada
- **THEN** la serie sigue con una sola ocurrencia, y el `currentBalance` de la cuenta no cambia

## MODIFIED Requirements

### Requirement: Listado de series activas
`GET /api/recurrences` SHALL devolver las series del usuario que no están canceladas y tienen
ocurrencias por venir: una serie con al menos una ocurrencia con `occurredAt` posterior al momento
actual, y una sin fin cuya cuenta está activa y no borrada y cuya categoría no está borrada. Cada una
SHALL traer los campos del alta y `nextOccurrenceAt`: la ocurrencia más próxima con fecha posterior
al momento actual o, en una serie sin fin que no tiene ninguna creada, la fecha de la que va a crear.
SHALL ir ordenadas por `nextOccurrenceAt`. Sin series activas, SHALL responder 200 con una lista vacía.

#### Scenario: Una activa y una terminada
- **WHEN** el usuario tiene una serie sin fin y otra con fin cuyas ocurrencias ya pasaron todas
- **THEN** la respuesta es 200 y solo trae la serie sin fin

#### Scenario: Una cancelada
- **WHEN** el usuario canceló una serie
- **THEN** la serie no aparece en el listado

#### Scenario: Series de otro usuario
- **WHEN** otro usuario tiene series activas
- **THEN** no aparecen en el listado del usuario autenticado

#### Scenario: Serie sin fin que ya no puede crear ocurrencias
- **WHEN** el usuario tiene series sin fin atrasadas, sin ocurrencias futuras, sobre una cuenta desactivada, sobre una cuenta borrada y con una categoría borrada
- **THEN** ninguna de las tres aparece en el listado
