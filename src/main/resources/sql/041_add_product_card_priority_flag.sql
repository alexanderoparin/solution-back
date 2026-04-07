-- Флаг приоритетной карточки для ускоренной обработки событий по nmID.
-- По умолчанию карточка не приоритетная.

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS is_priority BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.product_cards.is_priority IS 'Признак приоритетной карточки: true — события по nmID выполняются в первую очередь';
