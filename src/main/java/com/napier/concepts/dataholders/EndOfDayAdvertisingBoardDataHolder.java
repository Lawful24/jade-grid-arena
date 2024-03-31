package com.napier.concepts.dataholders;

import com.napier.concepts.AgentContact;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A wrapper for daily statistics collected by the Advertising agent at the end of a day.
 *
 * @author László Tárkányi
 *
 * @param contacts (ArrayList of AgentContacts) The agent contacts of all participating Household agents in the current simulation set.
 * @param numOfSocialAgents (int) The size of the social population.
 * @param numOfSelfishAgents (int) The size of the selfish population.
 * @param averageSocialSatisfaction (double) The average satisfaction of the social Household agent population.
 * @param averageSelfishSatisfaction (double) The average satisfaction of the selfish Household agent population.
 * @param averageSocialSatisfactionStandardDeviation The standard deviation of the average satisfaction of the social population.
 * @param averageSelfishSatisfactionStandardDeviation The standard deviation of the average satisfaction of the selfish population.
 * @param socialStatisticalValues (AgentStatisticalValuesPerStrategyType) The daily statistics of the social population.
 * @param selfishStatisticalValues The daily statistics of the selfish population.
 * @param initialRandomAllocationAverageSatisfaction The overall average satisfaction with the initially allocated timeslots.
 * @param optimumAveragePossibleSatisfaction The highest possible average satisfaction that the population can achieve through exchange based on the initially allocated timeslots.
 */
public record EndOfDayAdvertisingBoardDataHolder(
        ArrayList<AgentContact> contacts,
        int numOfSocialAgents,
        int numOfSelfishAgents,
        double averageSocialSatisfaction,
        double averageSelfishSatisfaction,
        double averageSocialSatisfactionStandardDeviation,
        double averageSelfishSatisfactionStandardDeviation,
        AgentStatisticalValuesPerStrategyType socialStatisticalValues,
        AgentStatisticalValuesPerStrategyType selfishStatisticalValues,
        double initialRandomAllocationAverageSatisfaction,
        double optimumAveragePossibleSatisfaction
) implements Serializable {
    // no-op
}