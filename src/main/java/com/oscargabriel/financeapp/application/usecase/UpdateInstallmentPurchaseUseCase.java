package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.port.in.UpdateInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateInstallmentPurchaseUseCase implements UpdateInstallmentPurchasePort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final InstallmentPurchaseRepositoryPort compras;
    private final Clock clock;

    /**
     * El formato y que el parche traiga algo ya los valido el request. Aqui queda que la categoria sea del
     * usuario y admita gastos. El update filtra por usuario y estado: cero filas es 404, sin lectura previa.
     */
    @Override
    public Mono<InstallmentPurchaseView> update(UUID userId, UUID purchaseId, UpdateInstallmentPurchaseCommand parche) {
        String descripcion = parche.description() == null ? null : parche.description().trim();
        return categoria(userId, parche.categoryId())
                .flatMap(categoria -> compras.update(purchaseId, userId, parche.scope(), clock.instant(),
                        descripcion, categoria.orElse(null)))
                .flatMap(actualizada -> actualizada
                        ? compras.findByIdAndUser(purchaseId, userId)
                        : Mono.<InstallmentPurchaseView>empty())
                .switchIfEmpty(Mono.error(UpdateInstallmentPurchaseUseCase::noEncontrada));
    }

    /** Sin categoria en el parche no hace falta leer nada del usuario. */
    private Mono<Optional<UUID>> categoria(UUID userId, String categoryId) {
        if (categoryId == null) {
            return Mono.just(Optional.empty());
        }
        return ReferenciasDelUsuario.de(userId, cuentas, categorias).map(referencias -> {
            List<ErrorDetail> errores = new ArrayList<>();
            UUID id = referencias.categoria(categoryId, TransactionType.EXPENSE, "categoryId", errores);
            if (!errores.isEmpty()) {
                throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
            }
            return Optional.of(id);
        });
    }

    /** Inexistente, ajena y cancelada dan lo mismo: distinguirlas confirmaria que el id existe. */
    static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La compra no existe", "id")));
    }
}