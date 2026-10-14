package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CreatedGroup;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;

/**
 * Solo viene el objeto que corresponde al intent; los otros, en null. Los tres son livianos (FA-148):
 * cuentas y categorias por nombre, y solo lo que el asistente le cuenta al usuario. Los totales
 * coinciden con los de /reports. Los instantes salen en UTC, como en el resto del API.
 */
public record AssistantMessageResponse(
        String intent,
        String message,
        GroupView group,
        ReportView report,
        BalanceView balance) {

    /** Lo registrado sin confirmar: el id con el que se confirma o se descarta, y sus movimientos (FIN-58). */
    public record GroupView(String id, List<TransactionView> transactions) {

        static GroupView from(CreatedGroup g) {
            return new GroupView(g.id().toString(), g.transactions().stream().map(TransactionView::from).toList());
        }
    }

    public record TransactionView(
            String id,
            String type,
            String status,
            BigDecimal amount,
            String currencyCode,
            String description,
            String occurredAt,
            String account,
            String destinationAccount,
            String category) {

        static TransactionView from(CreatedMovement m) {
            return new TransactionView(m.id().toString(), m.type().name(), m.status(), m.amount(), m.currencyCode(),
                    m.description(), m.occurredAt().toString(), m.account(), m.destinationAccount(), m.category());
        }
    }

    public record ReportView(
            String from,
            String to,
            String currencyCode,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            List<ReportItem> transactions) {

        static ReportView from(AssistantReport r) {
            return new ReportView(r.from().toString(), r.to().toString(), r.currencyCode(), r.income(), r.expense(),
                    r.net(), r.transactions().stream().map(ReportItem::from).toList());
        }
    }

    public record ReportItem(
            String id,
            String type,
            BigDecimal amount,
            String currencyCode,
            String description,
            String occurredAt,
            String account,
            String category) {

        static ReportItem from(AssistantReport.Item t) {
            return new ReportItem(t.id().toString(), t.type().name(), t.amount(), t.currencyCode(), t.description(),
                    t.occurredAt().toString(), t.account(), t.category());
        }
    }

    public record BalanceView(
            String from,
            String to,
            String currencyCode,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            BigDecimal allTimeNet,
            List<AccountView> accounts) {

        static BalanceView from(AssistantBalance b) {
            return new BalanceView(b.from().toString(), b.to().toString(), b.currencyCode(), b.income(), b.expense(),
                    b.net(), b.allTimeNet(), b.accounts().stream().map(AccountView::from).toList());
        }
    }

    public record AccountView(String name, String currencyCode, BigDecimal currentBalance) {

        static AccountView from(AssistantBalance.AccountBalance c) {
            return new AccountView(c.name(), c.currencyCode(), c.currentBalance());
        }
    }

    public static AssistantMessageResponse from(AssistantReply respuesta) {
        return new AssistantMessageResponse(
                respuesta.intent().name(),
                respuesta.message(),
                respuesta.group() == null ? null : GroupView.from(respuesta.group()),
                respuesta.report() == null ? null : ReportView.from(respuesta.report()),
                respuesta.balance() == null ? null : BalanceView.from(respuesta.balance()));
    }
}
