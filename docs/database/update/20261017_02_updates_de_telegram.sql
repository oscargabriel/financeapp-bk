-- =============================================================================
-- 2026-10-17 · 02 · Los updates de Telegram atendidos (FIN-61)
--
-- Crea finance.telegram_updates, donde el webhook del bot anota cada update_id
-- que atiende para no procesar dos veces un reenvío de Telegram. La app borra
-- las filas de más de dos días.
--
-- Solo crea una tabla: NO reinicia los datos de la base local. En producción
-- se aplica ANTES de desplegar la app de FIN-61: la app nueva la usa desde el
-- primer update.
--
--   psql -U <usuario> -d financeapp -f 20261017_02_updates_de_telegram.sql
-- =============================================================================

BEGIN;

CREATE TABLE finance.telegram_updates (
    update_id    BIGINT       PRIMARY KEY,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  finance.telegram_updates IS 'Updates del webhook de Telegram ya atendidos (FIN-61), para que un reenvío no registre ni conteste dos veces. La app inserta cada update al recibirlo y borra los de más de dos días: Telegram guarda los no entregados hasta 24 horas, y tras una semana sin updates elige el siguiente update_id al azar.';
COMMENT ON COLUMN finance.telegram_updates.update_id IS 'El update_id que manda Telegram. Único por bot.';
COMMENT ON COLUMN finance.telegram_updates.received_at IS 'Cuándo llegó el update por primera vez. Las filas de más de dos días se borran al atender el siguiente.';

COMMIT;

-- -----------------------------------------------------------------------------
-- Reversión. La app de FIN-61 deja de atender updates sin la tabla: revertir
-- también el despliegue.
--
-- DROP TABLE finance.telegram_updates;
-- -----------------------------------------------------------------------------
