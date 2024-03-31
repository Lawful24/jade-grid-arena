package com.napier.concepts.dataholders;

import java.io.Serializable;

/**
 * A wrapper for exchange statistics collected by a Household agent at the end an exchange round.
 *
 * @author László Tárkányi
 *
 * @param satisfaction The satisfaction of an agent at the end of an exchange round.
 * @param isTradeOfferReceiver Whether the agent is a receiver of a trade offer or not. TODO: there might be a need for another parameter
 * @param exchangeRoundHouseholdCPUTime The number of nanoseconds it took for an agent to complete the given exchange round.
 */
public record EndOfExchangeHouseholdDataHolder(
        double satisfaction,
        boolean isTradeOfferReceiver,
        long exchangeRoundHouseholdCPUTime
) implements Serializable {
    // no-op
}