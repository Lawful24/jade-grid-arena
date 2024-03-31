package com.napier.concepts.dataholders;

import java.io.Serializable;

/**
 * A wrapper for exchange statistics collected by a Household agent at the end an exchange round.
 *
 * @author László Tárkányi
 *
 * @param satisfaction The satisfaction of an agent at the end of an exchange round.
 * @param isTradeOfferRequester Whether the agent is a requester of a trade offer or not.
 * @param isTradeOfferReceiver Whether the agent is a receiver of a trade offer or not.
 * @param exchangeRoundHouseholdCPUTime The number of nanoseconds it took for an agent to complete the given exchange round.
 */
public record EndOfExchangeHouseholdDataHolder(
        double satisfaction,
        boolean isTradeOfferRequester,
        boolean isTradeOfferReceiver,
        long exchangeRoundHouseholdCPUTime
) implements Serializable {
    // no-op
}