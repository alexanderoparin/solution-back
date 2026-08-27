package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.oparin.solution.model.OzonSellerSubscriptionType;

import static org.junit.jupiter.api.Assertions.*;

class OzonSellerInfoResponseTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void parsesTypeUnderscoreFromFlatResponse() throws Exception {
        OzonSellerInfoResponse info = mapper.readValue(
                "{\"subscription\":{\"is_premium\":false,\"type_\":\"UNSPECIFIED\"}}",
                OzonSellerInfoResponse.class);

        OzonSellerInfoResponse.Subscription subscription = info.resolveSubscription();

        assertFalse(subscription.getPremium());
        assertEquals("UNSPECIFIED", subscription.resolveTypeRaw());
        assertEquals(OzonSellerSubscriptionType.UNSPECIFIED,
                OzonSellerInfoResponse.resolveSubscriptionType(subscription));
    }

    @Test
    void prefersRootSubscriptionOverLegacyResult() throws Exception {
        OzonSellerInfoResponse info = mapper.readValue(
                "{\"result\":{\"subscription\":{\"is_premium\":true,\"type\":\"PREMIUM\"}},"
                        + "\"subscription\":{\"is_premium\":false,\"type_\":\"UNSPECIFIED\"}}",
                OzonSellerInfoResponse.class);

        OzonSellerInfoResponse.Subscription subscription = info.resolveSubscription();

        assertFalse(subscription.getPremium());
        assertEquals("UNSPECIFIED", subscription.resolveTypeRaw());
        assertEquals(OzonSellerSubscriptionType.UNSPECIFIED,
                OzonSellerInfoResponse.resolveSubscriptionType(subscription));
    }

    @Test
    void prefersTypeUnderscoreOverLegacyTypeField() throws Exception {
        OzonSellerInfoResponse info = mapper.readValue(
                "{\"subscription\":{\"is_premium\":true,\"type_\":\"UNSPECIFIED\",\"type\":\"PREMIUM\"}}",
                OzonSellerInfoResponse.class);

        OzonSellerInfoResponse.Subscription subscription = info.resolveSubscription();

        assertEquals("UNSPECIFIED", subscription.resolveTypeRaw());
        assertEquals(OzonSellerSubscriptionType.UNSPECIFIED,
                OzonSellerInfoResponse.resolveSubscriptionType(subscription));
    }

    @Test
    void parsesLegacyTypeFieldWhenTypeUnderscoreAbsent() throws Exception {
        OzonSellerInfoResponse info = mapper.readValue(
                "{\"result\":{\"subscription\":{\"is_premium\":true,\"type\":\"PREMIUM_PLUS\"}}}",
                OzonSellerInfoResponse.class);

        OzonSellerInfoResponse.Subscription subscription = info.resolveSubscription();

        assertTrue(subscription.getPremium());
        assertEquals(OzonSellerSubscriptionType.PREMIUM_PLUS,
                OzonSellerInfoResponse.resolveSubscriptionType(subscription));
    }

    @Test
    void makesevaLikeResponseWithOnlyLegacyTypeField() throws Exception {
        OzonSellerInfoResponse info = mapper.readValue(
                "{\"subscription\":{\"is_premium\":true,\"type\":\"PREMIUM\"}}",
                OzonSellerInfoResponse.class);

        OzonSellerInfoResponse.Subscription subscription = info.resolveSubscription();

        assertTrue(subscription.getPremium());
        assertEquals("PREMIUM", subscription.resolveTypeRaw());
        assertEquals(OzonSellerSubscriptionType.PREMIUM,
                OzonSellerInfoResponse.resolveSubscriptionType(subscription));
    }
}
