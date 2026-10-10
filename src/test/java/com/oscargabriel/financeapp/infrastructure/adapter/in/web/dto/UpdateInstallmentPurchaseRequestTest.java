package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static com.oscargabriel.financeapp.support.InstallmentMother.unCuerpoDeParche;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;
import com.oscargabriel.financeapp.support.Violaciones;

class UpdateInstallmentPurchaseRequestTest {

    @Test
    void soloElAlcanceEsObligatorio() {
        assertThat(Violaciones.de(unCuerpoDeParche().request())).isEmpty();
    }

    @Test
    void unParcheCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(unCuerpoDeParche().scope("all").description("x".repeat(255))
                .categoryId(TransactionMother.MERCADO_ID.toString()).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin alcance", unCuerpoDeParche().scope(null), "scope",
                        "El alcance es obligatorio: FUTURE o ALL"),
                Arguments.of("alcance desconocido", unCuerpoDeParche().scope("PAST"), "scope",
                        "El alcance debe ser FUTURE o ALL"),
                Arguments.of("descripcion en blanco", unCuerpoDeParche().description(" "), "description",
                        "La descripcion no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("descripcion de 256", unCuerpoDeParche().description("x".repeat(256)), "description",
                        "La descripcion no puede superar los 255 caracteres"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, InstallmentMother.CuerpoDeParche parche, String campo,
            String texto) {
        assertThat(Violaciones.de(parche.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void soloElAlcanceNoTraeCambios() {
        assertThat(unCuerpoDeParche().request().sinCambios()).isTrue();
        assertThat(unCuerpoDeParche().description("TV").request().sinCambios()).isFalse();
        assertThat(unCuerpoDeParche().categoryId("x").request().sinCambios()).isFalse();
    }

    @Test
    void convierteAlComando() {
        UpdateInstallmentPurchaseCommand parche = unCuerpoDeParche().scope(" all ").description("TV").request()
                .toCommand();

        assertThat(parche.scope()).isEqualTo(GroupScope.ALL);
        assertThat(parche.description()).isEqualTo("TV");
        assertThat(parche.categoryId()).isNull();
    }
}