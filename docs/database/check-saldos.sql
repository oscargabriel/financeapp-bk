-- =============================================================================
-- financeapp-bk : el saldo de cada cuenta cuadra con sus movimientos (FA-29)
--
-- trg_transactions_sync_balance mantiene accounts.current_balance y la suite de
-- Gradle no tiene base: esta comprobación es su cobertura. Revisa TODAS las
-- cuentas de todos los usuarios. El saldo esperado es
--   initial_balance
--   + el efecto de los movimientos CONFIRMED que salen de la cuenta (balance_delta)
--   + lo que entra por transferencias CONFIRMED: destination_amount entre monedas
--     distintas (FA-51), amount si es NULL.
-- Los programados (fecha futura) cuentan: el trigger ya los aplicó. Los PENDING no.
--
-- Solo lee, dentro de una transacción READ ONLY. Con alguna cuenta descuadrada
-- avisa cuál y termina con error, así que psql sale con código distinto de 0.
-- verificar-bruno.ps1 lo corre solo después de cada corrida de la colección.
--
-- Ejecutar:  psql -U postgres -d financeapp -v ON_ERROR_STOP=1 -f docs/database/check-saldos.sql
-- =============================================================================

BEGIN READ ONLY;

DO $$
DECLARE
    v_cuenta     RECORD;
    v_cuentas    INTEGER;
    v_descuadres INTEGER := 0;
BEGIN
    SELECT count(*) INTO v_cuentas FROM finance.accounts;

    FOR v_cuenta IN
        SELECT u.email, a.name, a.currency_code, a.current_balance, esperado.saldo
          FROM finance.accounts a
          JOIN finance.users u ON u.id = a.user_id
         CROSS JOIN LATERAL (
               SELECT a.initial_balance
                      + COALESCE((SELECT SUM(finance.balance_delta(t.type, t.amount))
                                    FROM finance.transactions t
                                   WHERE t.account_id = a.id
                                     AND t.status = 'CONFIRMED'), 0)
                      + COALESCE((SELECT SUM(COALESCE(t.destination_amount, t.amount))
                                    FROM finance.transactions t
                                   WHERE t.destination_account_id = a.id
                                     AND t.status = 'CONFIRMED'), 0) AS saldo
               ) esperado
         WHERE a.current_balance <> esperado.saldo
         ORDER BY u.email, a.name
    LOOP
        v_descuadres := v_descuadres + 1;
        RAISE WARNING 'Descuadre: % / % (%): saldo %, esperado %',
            v_cuenta.email, v_cuenta.name, v_cuenta.currency_code, v_cuenta.current_balance, v_cuenta.saldo;
    END LOOP;

    IF v_descuadres > 0 THEN
        RAISE EXCEPTION '% de % cuentas con el saldo descuadrado', v_descuadres, v_cuentas;
    END IF;
    RAISE NOTICE 'Saldos: las % cuentas cuadran con sus movimientos confirmados', v_cuentas;
END;
$$;

COMMIT;
