package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.RecurrenceScope;
import com.oscargabriel.financeapp.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateRecurrenceRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateRecurrenceRequest;

/** Series de prueba. HOY es un viernes; los ids de cuenta y categoria son los de TransactionMother. */
public final class RecurrenceMother {

    public static final UUID SERIE_ID = UUID.fromString("80000000-0000-7000-8000-000000000001");

    /** Viernes 9 de octubre de 2026. */
    public static final LocalDate HOY = LocalDate.of(2026, 10, 9);

    private RecurrenceMother() {
    }

    public static Serie unaSerie() {
        return new Serie();
    }

    public static Alta unAlta() {
        return new Alta();
    }

    public static Parche unParche(RecurrenceScope alcance) {
        return new Parche(alcance);
    }

    public static Cuerpo unCuerpo() {
        return new Cuerpo();
    }

    public static CuerpoDeParche unCuerpoDeParche() {
        return new CuerpoDeParche();
    }

    /** Builder de una serie: mensual el 15, sin fin, sin nada creado todavia. */
    public static final class Serie {

        private UUID id = SERIE_ID;
        private TransactionType type = TransactionType.EXPENSE;
        private UUID accountId = TransactionMother.ORIGEN_ID;
        private UUID categoryId = TransactionMother.MERCADO_ID;
        private BigDecimal amount = new BigDecimal("44900");
        private String description = "Netflix";
        private RecurrenceRule rule = RecurrenceRule.mensual(1, 15, HOY);
        private LocalDate endDate;
        private Integer occurrenceLimit;
        private int generatedCount;
        private int priorCount;
        private RecurrenceStatus status = RecurrenceStatus.ACTIVE;

        public Serie id(UUID valor) {
            this.id = valor;
            return this;
        }

        public Serie type(TransactionType valor) {
            this.type = valor;
            return this;
        }

        public Serie semanal(int interval, DayOfWeek dia, LocalDate inicio) {
            this.rule = RecurrenceRule.semanal(interval, dia, inicio);
            return this;
        }

        public Serie mensual(int interval, int dia, LocalDate inicio) {
            this.rule = RecurrenceRule.mensual(interval, dia, inicio);
            return this;
        }

        public Serie hasta(LocalDate valor) {
            this.endDate = valor;
            return this;
        }

        public Serie veces(Integer valor) {
            this.occurrenceLimit = valor;
            return this;
        }

        public Serie creadas(int valor) {
            this.generatedCount = valor;
            return this;
        }

        public Serie anteriores(int valor) {
            this.priorCount = valor;
            return this;
        }

        public Serie cancelada() {
            this.status = RecurrenceStatus.CANCELLED;
            return this;
        }

        public Recurrence build() {
            return new Recurrence(id, TransactionMother.USER_ID, type, accountId, categoryId, amount, "COP",
                    description, rule, endDate, occurrenceLimit, generatedCount, priorCount, status);
        }
    }

    /** Builder del alta: mensual el 15 desde HOY, tres veces, como llega ya validada del controlador. */
    public static final class Alta {

        private TransactionType type = TransactionType.EXPENSE;
        private String accountId = TransactionMother.ORIGEN_ID.toString();
        private String categoryId = TransactionMother.MERCADO_ID.toString();
        private BigDecimal amount = new BigDecimal("44900");
        private String description = "  Netflix  ";
        private Frequency frequency = Frequency.MONTHLY;
        private int interval = 1;
        private DayOfWeek dayOfWeek;
        private Integer dayOfMonth = 15;
        private LocalDate startDate = HOY;
        private LocalDate endDate;
        private Integer occurrences = 3;

        public Alta type(TransactionType valor) {
            this.type = valor;
            return this;
        }

        public Alta accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Alta categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Alta semanal(DayOfWeek dia) {
            this.frequency = Frequency.WEEKLY;
            this.dayOfWeek = dia;
            this.dayOfMonth = null;
            return this;
        }

        public Alta mensual(int dia) {
            this.frequency = Frequency.MONTHLY;
            this.dayOfWeek = null;
            this.dayOfMonth = dia;
            return this;
        }

        public Alta desde(LocalDate valor) {
            this.startDate = valor;
            return this;
        }

        public Alta hasta(LocalDate valor) {
            this.endDate = valor;
            this.occurrences = null;
            return this;
        }

        public Alta veces(Integer valor) {
            this.occurrences = valor;
            this.endDate = null;
            return this;
        }

        public Alta sinFin() {
            this.endDate = null;
            this.occurrences = null;
            return this;
        }

        public CreateRecurrenceCommand build() {
            return new CreateRecurrenceCommand(type, accountId, categoryId, amount, description, frequency, interval,
                    dayOfWeek, dayOfMonth, startDate, endDate, occurrences);
        }
    }

    /** Builder del parche de una serie: todo en null, que es "no cambia", salvo el alcance. */
    public static final class Parche {

        private final RecurrenceScope scope;
        private String accountId;
        private String categoryId;
        private BigDecimal amount;
        private String description;
        private Frequency frequency;
        private Integer interval;
        private DayOfWeek dayOfWeek;
        private Integer dayOfMonth;

        private Parche(RecurrenceScope scope) {
            this.scope = scope;
        }

        public Parche accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Parche categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Parche amount(String valor) {
            this.amount = new BigDecimal(valor);
            return this;
        }

        public Parche description(String valor) {
            this.description = valor;
            return this;
        }

        public Parche frequency(Frequency valor) {
            this.frequency = valor;
            return this;
        }

        public Parche interval(Integer valor) {
            this.interval = valor;
            return this;
        }

        public Parche dayOfWeek(DayOfWeek valor) {
            this.dayOfWeek = valor;
            return this;
        }

        public Parche dayOfMonth(Integer valor) {
            this.dayOfMonth = valor;
            return this;
        }

        public UpdateRecurrenceCommand build() {
            return new UpdateRecurrenceCommand(scope, accountId, categoryId, amount, description, frequency, interval,
                    dayOfWeek, dayOfMonth);
        }
    }

    /** El cuerpo del alta como llega en JSON: mensual el 15 desde 2026-10-15, tres veces. */
    public static final class Cuerpo {

        private String type = "EXPENSE";
        private String accountId = TransactionMother.ORIGEN_ID.toString();
        private String categoryId = TransactionMother.MERCADO_ID.toString();
        private BigDecimal amount = new BigDecimal("44900");
        private String description = "Netflix";
        private String frequency = "MONTHLY";
        private Integer interval;
        private String dayOfWeek;
        private Integer dayOfMonth = 15;
        private String startDate = "2026-10-15";
        private String endDate;
        private Integer occurrences = 3;

        public Cuerpo type(String valor) {
            this.type = valor;
            return this;
        }

        public Cuerpo accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Cuerpo categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Cuerpo amount(BigDecimal valor) {
            this.amount = valor;
            return this;
        }

        public Cuerpo description(String valor) {
            this.description = valor;
            return this;
        }

        public Cuerpo frequency(String valor) {
            this.frequency = valor;
            return this;
        }

        public Cuerpo interval(Integer valor) {
            this.interval = valor;
            return this;
        }

        public Cuerpo dayOfWeek(String valor) {
            this.dayOfWeek = valor;
            return this;
        }

        public Cuerpo dayOfMonth(Integer valor) {
            this.dayOfMonth = valor;
            return this;
        }

        public Cuerpo startDate(String valor) {
            this.startDate = valor;
            return this;
        }

        public Cuerpo endDate(String valor) {
            this.endDate = valor;
            return this;
        }

        public Cuerpo occurrences(Integer valor) {
            this.occurrences = valor;
            return this;
        }

        public CreateRecurrenceRequest request() {
            return new CreateRecurrenceRequest(type, accountId, categoryId, amount, description, frequency, interval,
                    dayOfWeek, dayOfMonth, startDate, endDate, occurrences);
        }
    }

    /** El cuerpo del parche como llega en JSON: solo el alcance FUTURE, que por si solo no cambia nada. */
    public static final class CuerpoDeParche {

        private String scope = "FUTURE";
        private String accountId;
        private String categoryId;
        private BigDecimal amount;
        private String description;
        private String frequency;
        private Integer interval;
        private String dayOfWeek;
        private Integer dayOfMonth;

        public CuerpoDeParche scope(String valor) {
            this.scope = valor;
            return this;
        }

        public CuerpoDeParche accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public CuerpoDeParche categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public CuerpoDeParche amount(BigDecimal valor) {
            this.amount = valor;
            return this;
        }

        public CuerpoDeParche description(String valor) {
            this.description = valor;
            return this;
        }

        public CuerpoDeParche frequency(String valor) {
            this.frequency = valor;
            return this;
        }

        public CuerpoDeParche interval(Integer valor) {
            this.interval = valor;
            return this;
        }

        public CuerpoDeParche dayOfWeek(String valor) {
            this.dayOfWeek = valor;
            return this;
        }

        public CuerpoDeParche dayOfMonth(Integer valor) {
            this.dayOfMonth = valor;
            return this;
        }

        public UpdateRecurrenceRequest request() {
            return new UpdateRecurrenceRequest(scope, accountId, categoryId, amount, description, frequency, interval,
                    dayOfWeek, dayOfMonth);
        }
    }
}
