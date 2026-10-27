-- Рейтинг берём с витрины (публичный catalog reviewRating), не из seller item-rating.
COMMENT ON COLUMN solution.wb_product_cards.rating IS
    'Рейтинг по отзывам на витрине WB (reviewRating из публичного catalog API), шкала 1–5.';
