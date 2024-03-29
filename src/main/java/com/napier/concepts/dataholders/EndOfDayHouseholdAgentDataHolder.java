package com.napier.concepts.dataholders;

import java.io.Serializable;

public record EndOfDayHouseholdAgentDataHolder(
        int numOfDailyRejectedReceivedExchanges,
        int numOfDailyRejectedRequestedExchanges,
        int numOfDailyAcceptedRequestedExchanges,
        int numOfDailyAcceptedReceivedExchangesWithSocialCapita,
        int numOfDailyAcceptedReceivedExchangesWithoutSocialCapita,
        int totalSocialCapita
) implements Serializable {
    // no-op
}
