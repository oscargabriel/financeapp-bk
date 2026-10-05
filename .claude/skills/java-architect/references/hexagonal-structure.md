# Estructura Hexagonal Canónica — Java 25 + Spring Boot 4

## Tabla de contenidos
1. [Layout de paquetes](#layout)
2. [Nomenclatura](#nomenclatura)
3. [Reglas de dependencia](#dependencias)
4. [Estructura de módulos Maven/Gradle](#build)
5. [Checklist por capa](#checklist)

---

## 1. Layout de paquetes {#layout}

```
src/
└── main/
    └── java/
        └── com/empresa/nombreapp/
            │
            ├── domain/                              ← CAPA DE DOMINIO (núcleo puro)
            │   ├── model/
            │   │   ├── Pedido.java                  ← entidad (clase final)
            │   │   ├── PedidoId.java                ← value object (record)
            │   │   ├── LineaPedido.java              ← value object (record)
            │   │   └── EstadoPedido.java             ← enum de estados
            │   ├── port/
            │   │   ├── in/                          ← puertos de entrada (driving)
            │   │   │   └── ConfirmarPedidoPort.java
            │   │   └── out/                         ← puertos de salida (driven)
            │   │       ├── PedidoRepository.java
            │   │       └── DomainEventPublisher.java
            │   ├── service/                         ← servicios de dominio puros
            │   │   └── PrecioCalculadorService.java
            │   └── event/                           ← domain events
            │       └── EventoPedido.java             ← sealed interface + records
            │
            ├── application/                         ← CAPA DE APLICACIÓN
            │   ├── usecase/
            │   │   ├── ConfirmarPedidoUseCase.java
            │   │   └── CrearPedidoUseCase.java
            │   └── dto/                             ← commands, queries y responses
            │       ├── ConfirmarPedidoCommand.java   ← record
            │       ├── CrearPedidoCommand.java        ← record
            │       └── PedidoConfirmadoDto.java       ← record
            │
            └── infrastructure/                     ← CAPA DE INFRAESTRUCTURA
                ├── adapter/
                │   ├── in/                          ← adaptadores de ENTRADA (driving)
                │   │   └── web/
                │   │       ├── PedidoController.java ← @RestController
                │   │       └── dto/                  ← request/response (NO junto al controller)
                │   │           ├── PedidoRequest.java   ← record de deserialización
                │   │           └── PedidoResponse.java  ← record de serialización
                │   └── out/                         ← adaptadores de SALIDA (driven)
                │       ├── persistence/
                │       │   ├── R2dbcPedidoRepository.java ← impl de PedidoRepository (reactivo)
                │       │   ├── PedidoR2dbcEntity.java      ← entidad R2DBC (distinta del dominio)
                │       │   └── PedidoEntityMapper.java     ← mapea entre domain y R2DBC entity
                │       └── messaging/
                │           ├── KafkaDomainEventPublisher.java ← impl de DomainEventPublisher
                │           └── KafkaConsumerAdapter.java
                └── config/                          ← wiring Spring (NO es adaptador)
                    └── ApplicationConfig.java         ← @Configuration + @Bean (wiring)
```

Segregación por dirección: `adapter/in/` agrupa lo que cumple los puertos de entrada
(`domain/port/in`), `adapter/out/` lo que cumple los de salida (`domain/port/out`).
`config/` queda FUERA de `adapter/` porque cablea, no adapta. Los Request/Response DTOs
del adaptador web van en `adapter/in/web/dto/`, nunca sueltos junto a los controllers.

La carpeta `test/` refleja la misma estructura. Los tests de dominio no usan Spring.
Los tests de adaptadores usan slices: `@WebFluxTest`, `@DataR2dbcTest`, `@SpringBootTest`.

---

## 2. Nomenclatura {#nomenclatura}

| Elemento | Convención | Ejemplo |
|---|---|---|
| Entidad de dominio | `NombreEntidad` (clase final) | `Pedido` |
| Value object simple | `record NombreConcept(...)` | `record Email(String valor)` |
| Value object con ID | `record NombreId(UUID valor)` | `record PedidoId(UUID valor)` |
| Puerto de entrada | `NombreAccionPort` | `ConfirmarPedidoPort` |
| Puerto de salida repo | `NombreRepository` | `PedidoRepository` |
| Puerto de salida evento | `NombrePublisher` | `DomainEventPublisher` |
| Servicio de dominio | `NombreService` (en `domain/service/`) | `PrecioCalculadorService` |
| Caso de uso | `NombreAccionUseCase` | `ConfirmarPedidoUseCase` |
| Command | `NombreAccionCommand` (record) | `ConfirmarPedidoCommand` |
| Query | `NombreConsultaQuery` (record) | `BuscarPedidoQuery` |
| Response DTO | `NombreDto` (record) | `PedidoConfirmadoDto` |
| Adaptador R2DBC | `R2dbcNombreRepository` | `R2dbcPedidoRepository` |
| Entidad R2DBC | `NombreR2dbcEntity` | `PedidoR2dbcEntity` |
| Entidad JPA (si aplica) | `NombreJpaEntity` | `PedidoJpaEntity` |
| Controlador REST | `NombreController` | `PedidoController` |
| Mapper de salida (persistencia) | `NombreEntityMapper` | `PedidoEntityMapper` |
| Mapper de entrada (web) | `NombreRequestMapper` / `NombreMultipartMapper` (en `adapter/in/web/`) | `PedidoRequestMapper` |
| Configuración Spring | `NombreConfig` | `ApplicationConfig` |

Regla: el nombre revela el rol, no el mecanismo. `PedidoRepository` es correcto en
`domain/port/out/`; `PedidoR2dbcRepository` ahí sería una violación que filtra un
detalle de infraestructura al dominio.

Ubicación de los adaptadores: las clases de entrada (`Controller`, consumers) van en
`infrastructure/adapter/in/<canal>/`; las de salida (`R2dbc*Repository`, `*Publisher`,
clientes HTTP, parsers) en `infrastructure/adapter/out/<tecnología>/`. Los Request/Response
del adaptador web van en `infrastructure/adapter/in/web/dto/`.

---

## 3. Reglas de dependencia {#dependencias}

### Dirección permitida

```
infrastructure  →  application  →  domain
                                    ↑
                         (solo en esta dirección)
```

### Reglas concretas

1. **`domain/`** no importa nada fuera de `java.*`, `java.util.*`, `java.time.*`.
   Sin Spring, sin JPA, sin R2DBC, sin Reactor. Cero frameworks.

2. **`application/`** importa solo de `domain/`. Puede importar `reactor.core`
   si los puertos de salida están definidos con `Mono`/`Flux` — pero solo los tipos
   del API reactivo, no beans de Spring.

3. **`infrastructure/`** importa de `application/` y `domain/`. Es el único lugar
   donde viven `@Component`, `@RestController`, `@Repository`, `@Configuration`,
   anotaciones de R2DBC, etc.

4. Los DTOs en `application/dto/` son `record` puros — sin anotaciones de Jackson,
   Bean Validation ni Spring. Las anotaciones de serialización van en los DTOs de
   `infrastructure/adapter/in/web/dto/`.

5. Un caso de uso puede depender de múltiples puertos de salida, pero nunca de otro
   caso de uso directamente (usar un orquestador de dominio si es necesario).

### Violaciones típicas a detectar

| Violación | Señal en el código |
|---|---|
| Dominio acoplado a R2DBC | `import io.r2dbc.*` en `domain/model/` |
| Dominio acoplado a Spring | `@Autowired`, `@Component` en `domain/` |
| Dominio acoplado a Reactor | `import reactor.core.*` en `domain/model/` |
| UseCase depende de infra | `import ...infrastructure...` en `application/usecase/` |
| Controller accede al repo | `@Autowired PedidoRepository` directo en `PedidoController` |
| Entidad R2DBC == dominio | Una sola clase con `@Table` y lógica de negocio |
| DTO de dominio en REST | Devolver `Pedido` desde un `@RestController` |
| DTO web junto al controller | `Request`/`Response` en el mismo paquete que el `@RestController` en vez de en `adapter/in/web/dto/` |
| Adaptador sin segregar dirección | Adaptadores sueltos en `infrastructure/` (o un `adapter/` plano) sin separar `in/` y `out/` |
| `config/` dentro de `adapter/` | El wiring Spring no es un adaptador — va en `infrastructure/config/` |
| Controller con conocimiento de dominio | `Map`/constante de reglas o validación de formato dentro del `@RestController`, en vez de enum/VO de dominio + mapper de entrada |

---

## 4. Estructura de módulos Maven/Gradle {#build}

Para proyectos medianos o grandes, separar en módulos:

```
nombreapp/
├── nombreapp-domain/         ← pom sin dependencias de framework; solo java.base
├── nombreapp-application/    ← depende de domain; puede tener reactor-core
├── nombreapp-infrastructure/ ← depende de application y domain; Spring Boot aquí
└── nombreapp-bootstrap/      ← @SpringBootApplication, main, wiring final
```

**pom.xml de domain** — dependencias:
```xml
<!-- Sin Spring, sin R2DBC, sin Reactor -->
<dependencies>
  <!-- solo si necesitas algo muy específico -->
</dependencies>
```

**pom.xml de application** — dependencias:
```xml
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-core</artifactId>
</dependency>
```

**pom.xml de infrastructure** — dependencias:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
```

En proyectos pequeños, un solo módulo con paquetes separados es suficiente.
No añadir módulos Maven hasta que la complejidad lo justifique (YAGNI).

---

## 5. Checklist por capa {#checklist}

**`domain/`**
- [ ] Cero imports de frameworks externos
- [ ] Entidades con constructor privado y factory methods
- [ ] Value objects como `record` con validación en constructor compacto
- [ ] Domain events como `sealed interface` + `record` implementations
- [ ] Puertos como interfaces puras (sin `default` methods con lógica de infra)

**`application/`**
- [ ] Cada caso de uso tiene un único método público principal
- [ ] Commands y Queries son `record` inmutables.
  > **Patrón pragmático — Request como Command:** En operaciones CRUD simples donde
  > el `Request` HTTP tiene validación completa (`@Valid`) y sus campos mapean 1:1
  > con lo que necesita el use case, el `Command` puede eliminarse y el `Request`
  > pasarse directamente al puerto de entrada. Mantener `Command` cuando: (a) el
  > controller lo construye con transformación real (conversión de enum, cálculo de
  > campos), (b) múltiples adaptadores de entrada comparten el mismo use case, o
  > (c) el Command es un concepto de dominio rico más allá de un contenedor de datos.
- [ ] Sin lógica de negocio (toda en `domain/`)
- [ ] Sin imports de `infrastructure/`
- [ ] Sin anotaciones de Spring en las clases de use case (solo en config)

**`infrastructure/`**
- [ ] Adaptadores segregados por dirección: entrada en `adapter/in/`, salida en `adapter/out/`
- [ ] Cada adaptador implementa exactamente un puerto
- [ ] Request/Response del adaptador web en `adapter/in/web/dto/`, no junto a los controllers
- [ ] Mappers separados (sin mezclar lógica de negocio con mapeo)
- [ ] Controllers mapean a DTOs de infra antes de cruzar el boundary
- [ ] Controllers sin conocimiento de dominio ni validación de reglas — delegan a mapper de entrada, Command y VO/enum de dominio
- [ ] Wiring en `config/` (fuera de `adapter/`) con `@Configuration` + `@Bean`
- [ ] Tests de adaptadores usan test slices apropiados
