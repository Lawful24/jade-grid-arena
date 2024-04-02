package com.napier.concepts.dataholders;

import java.io.Serializable;

/**
 * A wrapper for daily statistics collected by a Household agent at the end of a day.
 *
 * @author László Tárkányi
 *
 * @param numOfDailyRejectedReceivedExchanges (int) The number of trade offers that the agent received and rejected during a given day.
 * @param numOfDailyRejectedRequestedExchanges (int) The number of trade offers that the agent requested and got rejected during a given day.
 * @param numOfDailyAcceptedRequestedExchanges (int) The number of trade offers that the agent requested and got accepted during a given day.
 * @param numOfDailyAcceptedReceivedExchangesWithSocialCapita (int) The number of trade offers that the agent received and accepted during a given day that involved social capita.
 * @param numOfDailyAcceptedReceivedExchangesWithoutSocialCapita (int) The number of trade offers that the agent received and accepted during a given day that did not involve social capita.
 * @param totalSocialCapita (int) The agent's total social capita at the end of a given day.
 */
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