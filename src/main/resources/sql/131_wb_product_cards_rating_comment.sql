-- Уточнение комментария: в rating храним рейтинг карточки (поле rating из item-rating), не feedbackRating.
COMMENT ON COLUMN solution.wb_product_cards.rating IS
    'Рейтинг карточки WB (поле rating из Analytics API item-rating), шкала 1–5. Совпадает с рейтингом на странице артикула.';
