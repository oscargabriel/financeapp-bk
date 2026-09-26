package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.List;

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
public class TransactionController {

    private final CreateTransactionsPort createTransactions;

    public TransactionController(CreateTransactionsPort createTransactions) {
        this.createTransactions = createTransactions;
    }

    /**
     * Mono de la lista y no Flux: transmitir el Flux mandaria el 201 y los primeros elementos antes de
     * que falle un INSERT posterior, y el cliente creeria que entraron aunque la base haga rollback.
     *
     * Un elemento null del arreglo se pasa como null: el caso de uso lo reporta con su indice.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<List<TransactionResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody List<CreateTransactionRequest> lote) {
        return Flux.defer(() -> createTransactions.create(UsuarioDelToken.de(jwt),
                        lote.stream().map(e -> e == null ? null : e.toCommand()).toList()))
                .map(TransactionResponse::from)
                .collectList();
    }
}
