package com.oscargabriel.financeapp.infrastructure.adapter.out.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.model.AssistantContext;
import com.oscargabriel.financeapp.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.domain.port.out.AssistantModelPort;

import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StubAssistantAdapterTest {

    private static final AssistantContext CONTEXTO = new AssistantContext(LocalDate.of(2026, 10, 8), List.of(),
            List.of());

    private final StubAssistantAdapter stub = new StubAssistantAdapter(JsonMapper.builder().build());

    @Test
    void entiendeUnaFuncionConSusArgumentos() {
        StepVerifier.create(stub.interpretar("""
                        crear_movimiento {"tipo":"EXPENSE","monto":20000,"cuenta":"nequi","categoria":"mercado",
                        "descripcion":"Mercado"}""", CONTEXTO))
                .expectNext(new AssistantDecision.CrearMovimiento("EXPENSE", "20000", "nequi", null, "mercado",
                        "Mercado", null))
                .verifyComplete();
    }

    @Test
    void entiendeUnaFuncionSinArgumentos() {
        StepVerifier.create(stub.interpretar("  consultar_saldo  ", CONTEXTO))
                .expectNext(new AssistantDecision.ConsultarSaldo(null, null))
                .verifyComplete();
    }

    @Test
    void cualquierOtroTextoEsSinFuncion() {
        StepVerifier.create(stub.interpretar("cuentame un chiste", CONTEXTO))
                .expectNext(new AssistantDecision.SinFuncion())
                .verifyComplete();
    }

    /** Igual que un cuerpo ilegible de Gemini: Bruno puede ejercer el 502 sin Google. */
    @Test
    void argumentosQueNoSonJsonSonUn502() {
        StepVerifier.create(stub.interpretar("crear_movimiento {no es json", CONTEXTO))
                .verifyErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(BadRequestException.class,
                        bre -> assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY)));
    }

    /** El conversor de Boot, que en la app lee "20s" como Duration y el runner no trae. */
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(StubAssistantAdapter.class, GeminiAssistantAdapter.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("asistente.gemini.api-key=clave", "asistente.gemini.model=modelo",
                    "asistente.gemini.base-url=http://localhost:65535", "asistente.gemini.timeout=1s");

    @Test
    void fueraDeProdElProveedorStubUsaElStub() {
        contexto.withPropertyValues("spring.profiles.active=local", "asistente.proveedor=stub")
                .run(ctx -> assertThat(ctx).getBean(AssistantModelPort.class).isInstanceOf(StubAssistantAdapter.class));
    }

    /** En Cloud Run no hay stub aunque alguien ponga la variable: sin adapter, el contexto no arranca. */
    @Test
    void enProdElProveedorStubNoDejaNingunAdapter() {
        contexto.withPropertyValues("spring.profiles.active=prod", "asistente.proveedor=stub")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AssistantModelPort.class));
    }

    @Test
    void elProveedorGeminiUsaGemini() {
        contexto.withPropertyValues("spring.profiles.active=prod", "asistente.proveedor=gemini")
                .run(ctx -> assertThat(ctx).getBean(AssistantModelPort.class)
                        .isInstanceOf(GeminiAssistantAdapter.class));
    }
}
