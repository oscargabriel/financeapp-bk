# Configuración de Logging en application.properties

Agregar o actualizar estas propiedades en `src/main/resources/application.properties`
(y en `application-local.properties` si existe perfil local).

```properties
# -----------------------------------------------------------------------------
# LOGGING
# -----------------------------------------------------------------------------

# Nivel general para toda la aplicacion
# WARN = solo advertencias y errores
# INFO = informacion general (recomendado produccion)
# DEBUG = depuracion detallada (recomendado desarrollo)
logging.level.root=INFO

# Frameworks de Spring (arranque, web, contexto, beans)
logging.level.org.springframework=INFO

# Driver de MongoDB y operaciones de base de datos
logging.level.org.mongodb.driver=INFO

# Logs de la aplicacion (ajustar segun paquete base del proyecto)
logging.level.mx.com.empresa.miapp=INFO

# Patron de salida en consola con requestId para trazabilidad
logging.pattern.console=%d{dd-MM-yyyy HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n
```

## Desglose del patrón de consola

| Segmento | Significado |
|---|---|
| `%d{dd-MM-yyyy HH:mm:ss.SSS}` | Timestamp con milisegundos |
| `[%thread]` | Nombre del hilo (ej. `reactor-http-nio-3`) |
| `%-5level` | Nivel alineado a 5 caracteres (INFO , DEBUG, ERROR) |
| `%logger{36}` | Nombre de la clase logger, máx 36 chars |
| `[%X{requestId}]` | Valor del MDC con clave `requestId` |
| `%msg%n` | Mensaje del log + salto de línea |

## Ajuste de niveles por entorno

```properties
# Perfil local (application-local.properties) — más detalle
logging.level.mx.com.empresa.miapp=DEBUG
logging.level.org.springframework.web=DEBUG

# Produccion — solo lo necesario
logging.level.root=WARN
logging.level.mx.com.empresa.miapp=INFO
```

## Salida esperada en consola

```
14-03-2026 10:00:01.123 [reactor-http-nio-3] INFO  mx.com.MiServicio    [a1b2c3d4-e5f6-...] - Procesando solicitud para id=123
14-03-2026 10:00:01.145 [boundedElastic-1]   DEBUG mx.com.MiRepositorio [a1b2c3d4-e5f6-...] - Consultando coleccion polizas
14-03-2026 10:00:01.200 [reactor-http-nio-3] INFO  mx.com.MiServicio    [a1b2c3d4-e5f6-...] - Respuesta enviada con 3 polizas
```

Notar que aunque los logs vienen de hilos distintos (`reactor-http-nio-3` y `boundedElastic-1`),
el `requestId` es el mismo — garantizado por `LoggingFilter` + `ReactorMdcHook`.
