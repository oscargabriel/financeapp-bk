# Modelado del dominio y construcción de objetos

Cómo se escriben entidades, value objects, eventos de dominio y records de datos en este stack
(Java 25 + Spring Boot 4). Léelo al crear o revisar tipos de `domain/model/` o DTOs.

## Entidades

Clases `final` con identidad explícita y estado mínimamente mutable. Los métodos que cambian
estado devuelven nuevas instancias, así una entidad no puede quedar en un estado intermedio
inválido si una operación falla a mitad de camino.

```java
public final class Pedido {
    private final PedidoId id;
    private final EstadoPedido estado;
    private final List<LineaPedido> lineas;

    private Pedido(PedidoId id, EstadoPedido estado, List<LineaPedido> lineas) {
        this.id = Objects.requireNonNull(id);
        this.estado = Objects.requireNonNull(estado);
        this.lineas = List.copyOf(lineas);
    }

    public static Pedido crear(List<LineaPedido> lineas) {
        return new Pedido(PedidoId.nuevo(), EstadoPedido.PENDIENTE, lineas);
    }

    public Pedido confirmar() {
        if (estado != EstadoPedido.PENDIENTE)
            throw new EstadoInvalidoException(id, estado);
        return new Pedido(id, EstadoPedido.CONFIRMADO, lineas);
    }
}
```

## Value objects

`record` con validación en el constructor compacto: el objeto inválido no llega a existir, que es
la validación de entrada más barata de sostener.

```java
public record Email(String valor) {
    public Email {
        if (valor == null || !valor.contains("@"))
            throw new IllegalArgumentException("Email inválido: " + valor);
        valor = valor.toLowerCase().strip();
    }
}
```

Un VO o enum de `domain/model/` expresa ausencia con `Optional` (o lanza una excepción de dominio
pura) y no importa Spring ni conoce `HttpStatus`: la traducción del error a HTTP es decisión del
adapter.

## Records de sub-estructura JSON

Cuando un `record` solo existe para mapear un sub-objeto JSON de **un** record contenedor (no es
un value object reutilizable), anídalo como `record` estático dentro del contenedor en vez de
dejarlo en un archivo suelto. Reduce DTOs de un solo campo sin cambiar la serialización: cada
`@JsonProperty` y `@JsonIgnoreProperties` se conserva idéntico.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Bill(
    @JsonProperty("documentNumber") DocumentNumber documentNumber,
    @JsonProperty("amount") Amount amount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentNumber(@JsonProperty("number") String number) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(@JsonProperty("amount") BigDecimal amount) {}
}
```

Reglas:
- El consumidor referencia el tipo cualificado (`Bill.DocumentNumber`); mismo paquete → sin import
  nuevo.
- Si **dos** agregados necesitan la misma forma, cada uno lleva su propia copia anidada. Compartir
  un tipo suelto acopla los agregados. Al puentear instancias se copia el valor:
  `new BillModifyRequest.DocumentNumber(bill.documentNumber().number())`.
- Excepción: no anides value objects de dominio con reuso genuino (`Email`, `Money`, ids de
  entidad).

## Construcción con builder

Todo `record`/clase de **datos** (DTOs de request/response/mensaje y records de datos con varios
campos) lleva Lombok `@Builder` y se construye con `.builder().campo(...).build()`, no con el
constructor posicional. Un constructor de 10-20 argumentos es frágil: agregar o quitar un campo
obliga a reordenar todas las llamadas y es fácil desalinear argumentos del mismo tipo (varios
`null` seguidos). El builder asigna por nombre, así que agregar campos no rompe ni desordena en
silencio.

```java
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtensionReceiptResult(
    @JsonProperty("receiptId") String receiptId,
    @JsonProperty("paymentDueDate") String paymentDueDate,
    @JsonProperty("documents") List<ProcessedDocument> documents
) {}

ExtensionReceiptResult r = ExtensionReceiptResult.builder()
    .receiptId(receiptId)
    .paymentDueDate(paymentDueDate)
    .documents(documents)
    .build();
```

Reglas:
- `@Builder` funciona sobre `record` (usa el constructor canónico) y es aditivo: conserva el
  constructor posicional y la serialización Jackson no cambia.
- Aplica también en los tests (Object Mother / fixtures).
- Excepciones que conservan su estilo: value objects con validación en constructor compacto
  (`Email`, `Money`) y entidades con factory method (`Pedido.crear(...)`).
- `@Data`/`@Getter` no van en records (generan sus propios accessors); `@Builder` es el único
  Lombok que se añade a los records de datos.

## Domain events

`sealed interface` con `record` implementors, para que el compilador exija exhaustividad en los
`switch` sin `default` cuando se añada un caso nuevo.

```java
public sealed interface EventoPedido
    permits PedidoCreado, PedidoConfirmado, PedidoCancelado {}

public record PedidoCreado(PedidoId id, Instant occurredOn) implements EventoPedido {}
public record PedidoConfirmado(PedidoId id, Instant occurredOn) implements EventoPedido {}
```
