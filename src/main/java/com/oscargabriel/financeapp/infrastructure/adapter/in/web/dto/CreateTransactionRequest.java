package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.FechaConOffset;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ReglasDeTransferencia;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * Un elemento del lote. Los ids y occurredAt llegan como texto para que un valor mal formado salga
 * como error de su indice y no como un cuerpo ilegible; destinationAmount, solo para rechazarlo.
 *
 * Aqui van las reglas que se deciden mirando solo el elemento. Que las cuentas y la categoria existan
 * y sean del usuario lo decide el caso de uso: por eso un id mal formado se reporta alli como "no existe".
 */
@ReglasDeTransferencia
public record CreateTransactionRequest(

        @NotBlank(message = "El tipo es obligatorio")
        @ValorDeEnum(value = TransactionType.class, message = "El tipo debe ser EXPENSE, INCOME o TRANSFER")
        String type,

        @NotBlank(message = "La cuenta es obligatoria")
        String accountId,

        String destinationAccountId,

        String categoryId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero: el signo lo da el tipo")
        @MontoNumeric(message = "El monto admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount,

        @Null(message = "destinationAmount no se admite mientras todas las cuentas sean COP")
        BigDecimal destinationAmount,

        /** Solo COP hasta FA-51, que registra cada movimiento en la moneda de su cuenta. */
        @Pattern(regexp = "(?i)^\\s*COP\\s*$", message = "Por ahora solo se admiten movimientos en COP")
        String currencyCode,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        /** notes es TEXT; el tope lo fija FA-27 para que un lote de 500 no pase del MB del codec por las notas. */
        @Size(max = 1000, message = "Las notas no pueden superar los 1000 caracteres")
        String notes,

        /** Opcional: sin fecha, el caso de uso pone el instante de la peticion (FA-60). */
        @FechaConOffset
        String occurredAt) {

    public CreateTransactionCommand toCommand() {
        return new CreateTransactionCommand(type, accountId, destinationAccountId, categoryId, amount,
                destinationAmount, currencyCode, description, notes, occurredAt);
    }
}
