-- Уточнение: в rating храним feedbackRating (звёзды 1–5), не seller «rating» карточки.
COMMENT ON COLUMN solution.wb_product_cards.rating IS
    'Рейтинг по отзывам WB (feedbackRating.current из Analytics API item-rating), шкала 1–5. Звёзды на странице артикула.';
