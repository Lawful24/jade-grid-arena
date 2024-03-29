package com.napier.concepts;

import java.io.Serializable;

public record EndOfExchangeHouseholdDataHolder(
        double satisfaction,
        boolean isTradeOfferReceiver,
        long exchangeRoundHouseholdCPUTime
) implements Serializable {
    // no-op
}
