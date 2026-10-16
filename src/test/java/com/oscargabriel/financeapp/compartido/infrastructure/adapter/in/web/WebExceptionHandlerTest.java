package com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cada case del switch y cada rama de detallesDe, fuera de Spring: el cableado real y el paso por
 * las cadenas de seguridad los cubre WebExceptionHandlerIT. Que ningun case quede dominado por su
 * supertipo lo garantiza el compilador; aqui se comprueba que cada subtipo sale con su propio code.
 */
class WebExceptionHandlerTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private WebExceptionHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = handler(JSON);
    }

    @Test
    void devuelveLaRespuestaDeUnaBadRequestExceptionTalCual() {
        var ex = new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "El correo ya esta registrado", "email");

        StepVerifier.create(manejar(ex))
                .assertNext(respuesta -> {
                    assertThat(respuesta.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(respuesta.errors()).containsExactly(ErrorDetail.of(
                            "DUPLICATE_RESOURCE", "El correo ya esta registrado", "email"));
                })
                .verifyComplete();
    }

    @Test
    void reportaUnNumberFormatExceptionConSuCodigoYNoConElDeSuSupertipo() {
        StepVerifier.create(manejar(new NumberFormatException("For input string: \"abc\"")))
                .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                        ErrorDetail.of("INVALID_NUMBER_FORMAT", "Formato numerico invalido", "parameter")))
                .verifyComplete();
    }

    @Test
    void reportaUnIllegalArgumentExceptionConSuMensaje() {
        StepVerifier.create(manejar(new IllegalArgumentException("El mes debe tener el formato yyyy-MM")))
                .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST, ErrorDetail.of(
                        "INVALID_ARGUMENT", "El mes debe tener el formato yyyy-MM", "parameter")))
                .verifyComplete();
    }

    @Nested
    class ServerWebInput {

        @Test
        void usaElReasonComoDescripcionCuandoLoTiene() {
            StepVerifier.create(manejar(new ServerWebInputException("Falta el cuerpo")))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("JSON_PARSING_ERROR", "Falta el cuerpo", "body")))
                    .verifyComplete();
        }

        @Test
        void usaUnaDescripcionGenericaSinReason() {
            StepVerifier.create(manejar(new ServerWebInputException(null)))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST, ErrorDetail.of(
                            "JSON_PARSING_ERROR", "Cuerpo de la peticion invalido", "body")))
                    .verifyComplete();
        }

        @Test
        void usaUnaDescripcionGenericaConReasonEnBlanco() {
            StepVerifier.create(manejar(new ServerWebInputException("   ")))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST, ErrorDetail.of(
                            "JSON_PARSING_ERROR", "Cuerpo de la peticion invalido", "body")))
                    .verifyComplete();
        }
    }

    @Nested
    class ResponseStatus {

        @Test
        void un404SaleComoRecursoNoEncontrado() {
            StepVerifier.create(manejar(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.NOT_FOUND,
                            ErrorDetail.of("NOT_FOUND", "Recurso no encontrado", "request")))
                    .verifyComplete();
        }

        @Test
        void un413SaleConElCodigoDeTamano() {
            StepVerifier.create(manejar(new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE)))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.CONTENT_TOO_LARGE, ErrorDetail.of(
                            "PAYLOAD_TOO_LARGE",
                            "El cuerpo supera el tamano maximo permitido; parte el lote en envios mas pequenos",
                            "body")))
                    .verifyComplete();
        }

        @Test
        void otroStatusConservaElStatusYSaleComoValidacionDeLaPeticion() {
            var ex = new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "metodo GET no soportado");

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.METHOD_NOT_ALLOWED, ErrorDetail.of(
                            "VALIDATION_ERROR", "La peticion no pudo ser procesada", "request")))
                    .verifyComplete();
        }
    }

    @Test
    void unaExcepcionNoManejadaSaleComo500SinElMensajeOriginal() {
        StepVerifier.create(manejar(new IllegalStateException("relation finance.users does not exist")))
                .assertNext(respuesta -> {
                    unico(respuesta, HttpStatus.INTERNAL_SERVER_ERROR,
                            ErrorDetail.of("INTERNAL_SERVER_ERROR", "Ocurrio un error inesperado", "server"));
                    assertThat(respuesta.cuerpo()).doesNotContain("finance.users");
                })
                .verifyComplete();
    }

    @Test
    void reportaLosErroresDeCampoYLosGlobalesDeUnWebExchangeBindException() throws Exception {
        var resultado = new MapBindingResult(new HashMap<>(), "account");
        resultado.rejectValue("name", "NotBlank", "El nombre es obligatorio");
        resultado.reject("CamposDeCredito", "El cupo y el corte van juntos");

        StepVerifier.create(manejar(new WebExchangeBindException(parametro(0), resultado)))
                .assertNext(respuesta -> {
                    assertThat(respuesta.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(respuesta.errors()).containsExactlyInAnyOrder(
                            ErrorDetail.of("VALIDATION_ERROR", "El nombre es obligatorio", "name"),
                            ErrorDetail.of("VALIDATION_ERROR", "El cupo y el corte van juntos", "unknown"));
                })
                .verifyComplete();
    }

    @Test
    void siFallaLaSerializacionRespondeUn500ConElCuerpoDeRespaldo() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new IllegalStateException("serializador roto"));

        StepVerifier.create(manejar(handler(roto), new IllegalArgumentException("monto invalido")))
                .assertNext(respuesta -> unico(respuesta, HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorDetail.of("INTERNAL_SERVER_ERROR", "Ocurrio un error inesperado", "server")))
                .verifyComplete();
    }

    /**
     * Las excepciones se arman sobre los parametros de {@link #lote}: el primero es un cuerpo, como el
     * alta en lote, y el segundo un query param.
     */
    @Nested
    class ValidacionDeMetodo {

        @Test
        void unErrorDeCampoDeUnElementoLlevaSuIndice() throws Exception {
            var ex = validacion(erroresDeElemento(3, errores -> errores.rejectValue("amount", "Positive",
                    "El monto debe ser positivo")));

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("VALIDATION_ERROR", "El monto debe ser positivo", "[3].amount")))
                    .verifyComplete();
        }

        @Test
        void unErrorDeObjetoDeUnElementoSaleConElIndiceSolo() throws Exception {
            var ex = validacion(erroresDeElemento(3, errores -> errores.reject("ReglasDeTransferencia",
                    "La cuenta destino es obligatoria")));

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("VALIDATION_ERROR", "La cuenta destino es obligatoria", "[3]")))
                    .verifyComplete();
        }

        @Test
        void unElementoNuloSaleConElIndiceSolo() throws Exception {
            var ex = validacion(new ParameterValidationResult(parametro(0), null,
                    List.of(mensaje("El elemento no puede ser nulo")), List.of(), 3, null, (error, tipo) -> null));

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("VALIDATION_ERROR", "El elemento no puede ser nulo", "[3]")))
                    .verifyComplete();
        }

        @Test
        void unErrorDeCampoSinIndiceSaleConElCampoSolo() throws Exception {
            var errores = new MapBindingResult(new HashMap<>(), "body");
            errores.rejectValue("amount", "Positive", "El monto debe ser positivo");
            var ex = validacion(new ParameterErrors(parametro(0), null, errores, null, null, null));

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("VALIDATION_ERROR", "El monto debe ser positivo", "amount")))
                    .verifyComplete();
        }

        @Test
        void unErrorDelCuerpoCompletoSaleComoBodyYOcultaLosDeSusElementos() throws Exception {
            var delCuerpo = new ParameterValidationResult(parametro(0), List.of(),
                    List.of(mensaje("El lote admite hasta 500 movimientos")), null, null, null, (error, tipo) -> null);
            var deUnElemento = erroresDeElemento(3, errores -> errores.rejectValue("amount", "Positive",
                    "El monto debe ser positivo"));

            StepVerifier.create(manejar(validacion(delCuerpo, deUnElemento)))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST, ErrorDetail.of(
                            "VALIDATION_ERROR", "El lote admite hasta 500 movimientos", "body")))
                    .verifyComplete();
        }

        @Test
        void unErrorDeUnParametroQueNoEsElCuerpoSaleConSuNombre() throws Exception {
            var ex = validacion(new ParameterValidationResult(parametro(1), "",
                    List.of(mensaje("La moneda es obligatoria")), null, null, null, (error, tipo) -> null));

            StepVerifier.create(manejar(ex))
                    .assertNext(respuesta -> unico(respuesta, HttpStatus.BAD_REQUEST,
                            ErrorDetail.of("VALIDATION_ERROR", "La moneda es obligatoria", "moneda")))
                    .verifyComplete();
        }

        private ParameterErrors erroresDeElemento(int indice,
                java.util.function.Consumer<MapBindingResult> rechazos) throws Exception {
            var errores = new MapBindingResult(new HashMap<>(), "item");
            rechazos.accept(errores);
            return new ParameterErrors(parametro(0), new Object(), errores, List.of(), indice, null);
        }

        private HandlerMethodValidationException validacion(ParameterValidationResult... resultados)
                throws Exception {
            return new HandlerMethodValidationException(
                    MethodValidationResult.create(this, metodoLote(), List.of(resultados)));
        }

        private DefaultMessageSourceResolvable mensaje(String texto) {
            return new DefaultMessageSourceResolvable(null, null, texto);
        }
    }

    @SuppressWarnings("unused")
    private void lote(@RequestBody List<Object> movimientos, @RequestParam String moneda) {
    }

    private static Method metodoLote() throws NoSuchMethodException {
        return WebExceptionHandlerTest.class.getDeclaredMethod("lote", List.class, String.class);
    }

    private static MethodParameter parametro(int indice) throws NoSuchMethodException {
        var parametro = new MethodParameter(metodoLote(), indice);
        parametro.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        return parametro;
    }

    private static void unico(Respuesta respuesta, HttpStatus status, ErrorDetail esperado) {
        assertThat(respuesta.status()).isEqualTo(status);
        assertThat(respuesta.errors()).containsExactly(esperado);
    }

    private Mono<Respuesta> manejar(Throwable ex) {
        return manejar(handler, ex);
    }

    private static Mono<Respuesta> manejar(WebExceptionHandler handler, Throwable ex) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/transactions").build());
        return handler.handle(exchange, ex)
                .then(Mono.defer(() -> exchange.getResponse().getBodyAsString()))
                .map(cuerpo -> new Respuesta(exchange.getResponse().getStatusCode(), errores(cuerpo), cuerpo));
    }

    private static WebExceptionHandler handler(ObjectMapper objectMapper) throws Exception {
        ServerCodecConfigurer configurer = ServerCodecConfigurer.create();
        var handler = new WebExceptionHandler(new DefaultErrorAttributes(), new WebProperties.Resources(),
                new StaticApplicationContext(), configurer, objectMapper);
        handler.setMessageReaders(configurer.getReaders());
        handler.afterPropertiesSet();
        return handler;
    }

    private static List<ErrorDetail> errores(String cuerpo) {
        JsonNode raiz = JSON.readTree(cuerpo);
        return raiz.get("errors").valueStream()
                .map(error -> ErrorDetail.of(error.get("code").asString(), error.get("description").asString(),
                        error.get("field").asString()))
                .toList();
    }

    private record Respuesta(HttpStatusCode status, List<ErrorDetail> errors, String cuerpo) {
    }
}
