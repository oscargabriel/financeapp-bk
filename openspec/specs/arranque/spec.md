# arranque Specification

## Purpose
Cuándo la aplicación se niega a arrancar por su configuración y qué dice al hacerlo. Así, un
despliegue incompleto falla en el acto con un error que nombra lo que falta, y no más tarde ni con
un síntoma que apunte a otra parte.

## Requirements

### Requirement: Credenciales de la base obligatorias al arrancar
La aplicación SHALL negarse a arrancar si el usuario o la contraseña de la conexión a la base no se
pueden resolver: ni la variable `DB_USERNAME`/`DB_PASSWORD` ni un perfil les dan valor. El error
SHALL nombrar la variable que falta y SHALL producirse antes de abrir el puerto, aunque la
verificación de conectividad de arranque esté apagada.

#### Scenario: Falta DB_USERNAME
- **WHEN** la app arranca con la configuración de Cloud Run, `DB_PASSWORD` definida, `DB_USERNAME` sin definir y `STARTUP_DB_CHECK_ENABLED=false`
- **THEN** el arranque aborta sin abrir el puerto y el error dice `Could not resolve placeholder 'DB_USERNAME'`

#### Scenario: Falta DB_PASSWORD
- **WHEN** la app arranca con la configuración de Cloud Run, `DB_USERNAME` definida, `DB_PASSWORD` sin definir y `STARTUP_DB_CHECK_ENABLED=false`
- **THEN** el arranque aborta sin abrir el puerto y el error dice `Could not resolve placeholder 'DB_PASSWORD'`

#### Scenario: El perfil da los valores sin las variables
- **WHEN** la app arranca con un perfil que fija el usuario y la contraseña directamente, como `local`, sin `DB_USERNAME` ni `DB_PASSWORD` en el entorno
- **THEN** el arranque no aborta por las credenciales

### Requirement: El error de arranque no expone credenciales
Cuando el arranque aborta porque falta una credencial de la base, el error SHALL NOT incluir el valor
de la otra credencial ni el de ningún otro secreto.

#### Scenario: Falta el usuario y la contraseña sí está
- **WHEN** el arranque aborta porque falta `DB_USERNAME` y `DB_PASSWORD` tiene un valor
- **THEN** ni el error ni el log de arranque contienen el valor de `DB_PASSWORD`

#### Scenario: Falta la contraseña y el usuario sí está
- **WHEN** el arranque aborta porque falta `DB_PASSWORD` y `DB_USERNAME` tiene un valor
- **THEN** el error no contiene el valor de `DB_USERNAME`
