package com.napier.concepts.dataholders;

import java.io.Serializable;

public record EndOfExchangeHouseholdDataHolder(
        double satisfaction,
        boolean isTradeOfferReceiver,
        long exchangeRoundHouseholdCPUTime
) implements Serializable {
    // no-op
}
