package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateTransactionRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.TransactionResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transactions")
@AllArgsConstructor
public class TransactionController {

    /**
     * El tope de FA-26: varios meses de extracto y, con elementos tipicos, bien por debajo del MB
     * del codec.
     */
    static final int TOPE_LOTE = 500;

    private final CreateTransactionsPort createTransactions;

    /**
     * Mono de la lista y no Flux: transmitir el Flux mandaria el 201 y los primeros elementos antes de
     * que falle un INSERT posterior, y el cliente creeria que entraron aunque la base haga rollback.
     *
     * Las constraints del parametro activan la validacion de metodo de Spring: el tamano del lote, los
     * elementos nulos y las reglas de cada record salen juntos, cada uno con su indice.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<List<TransactionResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody
            @Size(min = 1, max = TOPE_LOTE, message = "El lote debe tener entre 1 y " + TOPE_LOTE + " movimientos")
            List<@NotNull(message = "El elemento no puede ser nulo") @Valid CreateTransactionRequest> lote) {
        return Flux.defer(() -> createTransactions.create(UsuarioDelToken.de(jwt),
                        lote.stream().map(CreateTransactionRequest::toCommand).toList()))
                .map(TransactionResponse::from)
                .collectList();
    }
}
