package com.napier.singletons;

import com.napier.concepts.dataholders.AgentStatisticalValuesPerStrategyType;
import com.napier.types.AgentStrategyType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A singleton class responsible for writing statistical data into files.
 *
 * @author László Tárkányi
 */
public class DataOutputSingleton {
    private static DataOutputSingleton instance;
    private String simulationDataOutputParentFolderPath;
    private String simulationDataOutputFolderPath;
    private FileWriter simulationDataTXTWriter;
    private FileWriter agentDataCSVWriter;
    private FileWriter dailyDataCSVWriter;
    private FileWriter exchangeDataCSVWriter;
    private FileWriter performanceDataCSVWriter;

    // Singleton
    private final SimulationConfigurationSingleton config;

    public static DataOutputSingleton getInstance() {
        if (instance == null) {
            instance = new DataOutputSingleton();
        }

        return instance;
    }

    public DataOutputSingleton() {
        this.config = SimulationConfigurationSingleton.getInstance();
    }

    /**
     * Creates the output files and initialises the file writers.
     *
     * @param doesUtiliseSocialCapita True if the simulation is currently configured to use social capita.
     * @param doesUtiliseSingleAgentType True if the simulation is currently configured to only use one of the 2 agent strategies.
     * @param selectedSingleAgentType The only agent type used currently in the simulation if it only uses one of the 2 agent strategies.
     */
    public void prepareSimulationDataOutput(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        this.createSimulationResultsFolderTree(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
        this.createAgentDataOutputFile();
        this.createExchangeDataOutputFile();
        this.createDailyDataOutputFile();
        this.createPerformanceDataOutputFile();
        this.createSimulationDataOutputFile(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
    }

    /**
     * Creates the folder tree for the data files.
     *
     * @param doesUtiliseSocialCapita True if the simulation is currently configured to use social capita.
     * @param doesUtiliseSingleAgentType True if the simulation is currently configured to only use one of the 2 agent strategies.
     * @param selectedSingleAgentType The only agent type used currently in the simulation if it only uses one of the 2 agent strategies.
     */
    private void createSimulationResultsFolderTree(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        // TODO: Cite Arena code
        // Create a directory to store the data output by all simulations being run.
        this.simulationDataOutputParentFolderPath = this.config.getResultsFolderPath() + "/" + this.config.getStartingSeed() + "/useSC_" + doesUtiliseSocialCapita + "_AType_";

        // Append the agent types used to the folder path
        if (!doesUtiliseSingleAgentType) {
            this.simulationDataOutputParentFolderPath += "mixed";
        } else {
            this.simulationDataOutputParentFolderPath += this.getAgentStrategyTypeCapString(selectedSingleAgentType);
        }

        // Append the exchange type to the folder path
        this.simulationDataOutputParentFolderPath += "_EType_" + this.config.getExchangeType();

        // Add subdirectory path
        this.simulationDataOutputFolderPath = this.simulationDataOutputParentFolderPath + "/data";

        try {
            // TODO: Cite Arena code
            // Create a directory to store the data output by the simulation.
            Files.createDirectories(Path.of(this.simulationDataOutputFolderPath));
        } catch (IOException e) {
            System.err.println("Error while trying to create the folder to store the results of the simulation in: " + e.getMessage());
        }
    }

    /**
     * Creates a new file or overwrites an existing one with the same name and writes the first row as a data index.
     */
    private void createAgentDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            File agentDataFile = new File(this.simulationDataOutputFolderPath, "agentData.csv");

            try {
                this.agentDataCSVWriter = new FileWriter(agentDataFile);

                // Write first row of the .csv file
                agentDataCSVWriter.append("Simulation Run,"); // TODO: convert these statements into an array and then map a function to convert it into csv data
                agentDataCSVWriter.append("Day,");
                agentDataCSVWriter.append("Agent Type,");
                agentDataCSVWriter.append("Satisfaction,");
                agentDataCSVWriter.append("Rejected Received Exchanges,");
                agentDataCSVWriter.append("Accepted Received Exchanges,");
                agentDataCSVWriter.append("Rejected Requested Exchanges,");
                agentDataCSVWriter.append("Accepted Requested Exchanges,");
                agentDataCSVWriter.append("Social Capita Exchanges,");
                agentDataCSVWriter.append("No Social Capita Exchanges,");
                agentDataCSVWriter.append("Unspent Social Capita");
                agentDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in agent data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    /**
     * Creates a new file or overwrites an existing one with the same name and writes the first row as a data index.
     */
    private void createDailyDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            File dailyDataFile = new File(this.simulationDataOutputFolderPath, "dailyData.csv");

            try {
                this.dailyDataCSVWriter = new FileWriter(dailyDataFile);

                // Write first row of the .csv file
                dailyDataCSVWriter.append("Simulation Run,");
                dailyDataCSVWriter.append("Day,");
                dailyDataCSVWriter.append("Social Pop,");
                dailyDataCSVWriter.append("Selfish Pop,");
                dailyDataCSVWriter.append("Social Sat,");
                dailyDataCSVWriter.append("Selfish Sat,");
                dailyDataCSVWriter.append("Social SD,");
                dailyDataCSVWriter.append("Selfish SD,");
                dailyDataCSVWriter.append("Social Upper Quartile,");
                dailyDataCSVWriter.append("Selfish Upper Quartile,");
                dailyDataCSVWriter.append("Social Lower Quartile,");
                dailyDataCSVWriter.append("Selfish Lower Quartile,");
                dailyDataCSVWriter.append("Social 95th Percentile,");
                dailyDataCSVWriter.append("Selfish 95th Percentile,");
                dailyDataCSVWriter.append("Social Max,");
                dailyDataCSVWriter.append("Selfish Max,");
                dailyDataCSVWriter.append("Social Min,");
                dailyDataCSVWriter.append("Selfish Min,");
                dailyDataCSVWriter.append("Social Median,");
                dailyDataCSVWriter.append("Selfish Median,");
                dailyDataCSVWriter.append("Random Allocation Sat,");
                dailyDataCSVWriter.append("Optimum Allocation Sat").append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in daily data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    /**
     * Creates a new file or overwrites an existing one with the same name and writes the first row as a data index.
     */
    private void createExchangeDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            File exchangeDataFile = new File(this.simulationDataOutputFolderPath, "exchangeData.csv");

            try {
                this.exchangeDataCSVWriter = new FileWriter(exchangeDataFile);

                // Write first row of the .csv file
                exchangeDataCSVWriter.append("Simulation Run,");
                exchangeDataCSVWriter.append("Day,");
                exchangeDataCSVWriter.append("Round,");
                exchangeDataCSVWriter.append("Agent Type,");
                exchangeDataCSVWriter.append("Satisfaction");
                exchangeDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in exchange data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    /**
     * Creates a new file or overwrites an existing one with the same name and writes the first row as a data index.
     */
    private void createPerformanceDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            File performanceDataFile = new File(this.simulationDataOutputFolderPath, "performanceData.csv");

            try {
                this.performanceDataCSVWriter = new FileWriter(performanceDataFile);

                // Write first row of the .csv file
                performanceDataCSVWriter.append("Simulation Run,");
                performanceDataCSVWriter.append("Day,");
                performanceDataCSVWriter.append("Round,");
                performanceDataCSVWriter.append("Strategy Type,");
                performanceDataCSVWriter.append("Requester/Receiver,");
                performanceDataCSVWriter.append("CPU Time Used (ns)");
                performanceDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in performance data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    /**
     * Creates a new file or overwrites an existing one with the same name and writes the first row as a data index.
     *
     * @param doesUtiliseSocialCapita True if the simulation is currently configured to use social capita.
     * @param doesUtiliseSingleAgentType True if the simulation is currently configured to only use one of the 2 agent strategies.
     * @param selectedSingleAgentType The only agent type used currently in the simulation if it only uses one of the 2 agent strategies.
     */
    private void createSimulationDataOutputFile(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        if (this.simulationDataOutputParentFolderPath != null) {
            // TODO: Cite Arena code
            File simulationDataFile = new File(this.simulationDataOutputParentFolderPath, "simulationData.txt");

            try {
                this.simulationDataTXTWriter = new FileWriter(simulationDataFile);

                // Write first row of the .csv file
                this.simulationDataTXTWriter.append("Simulation Information: \n\n");
                this.simulationDataTXTWriter.append("Seed: ").append(String.valueOf(this.config.getCurrentSeed())).append("\n");
                this.simulationDataTXTWriter.append("Single agent type: ").append(String.valueOf(doesUtiliseSingleAgentType)).append("\n");

                if (doesUtiliseSingleAgentType) {
                    this.simulationDataTXTWriter.append("Agent type: ").append(String.valueOf(selectedSingleAgentType)).append("\n");
                }

                this.simulationDataTXTWriter.append("Use social capita: ").append(String.valueOf(doesUtiliseSocialCapita)).append("\n");
                this.simulationDataTXTWriter.append("Simulation runs: ").append(String.valueOf(this.config.getNumOfSimulationRuns())).append("\n");
                this.simulationDataTXTWriter.append("Days after strategy takeover: ").append(String.valueOf(this.config.getNumOfAdditionalDaysAfterTakeover())).append("\n");
                this.simulationDataTXTWriter.append("Population size: ").append(String.valueOf(this.config.getPopulationCount())).append("\n");
                this.simulationDataTXTWriter.append("Unique time-slots: ").append(String.valueOf(this.config.getNumOfUniqueTimeSlots())).append("\n");
                this.simulationDataTXTWriter.append("Slots per agent: ").append(String.valueOf(this.config.getNumOfSlotsPerAgent())).append("\n");
                this.simulationDataTXTWriter.append("Number of agents to evolve: ").append(String.valueOf(this.config.getNumOfAgentsToEvolve())).append("\n");
                this.simulationDataTXTWriter.append("Starting ratio of agent types: ")
                        .append(this.getAgentStrategyTypeCapString(AgentStrategyType.SELFISH))
                        .append(" ")
                        .append(String.valueOf(this.config.getSelfishPopulationCount()))
                        .append(" : ")
                        .append(String.valueOf(this.config.getPopulationCount() - this.config.getSelfishPopulationCount()))
                        .append(" ")
                        .append(this.getAgentStrategyTypeCapString(AgentStrategyType.SOCIAL)); // TODO: this might have to be reworked but definitely has to be tested
                this.simulationDataTXTWriter.append("\n\n");
            } catch (IOException e) {
                System.err.println("Could not write in exchange data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    /**
     * Append a record to the agent data file.
     *
     * @param currentSimulationRun The number of a given simulation run in a simulation set.
     * @param currentDay The number of a given day in a simulation run.
     * @param agentStrategyType The strategy enum type of the Household agent.
     * @param currentSatisfaction The satisfaction of the Household agent.
     * @param numOfDailyRejectedReceivedExchanges The number of trade offers that the agent received and rejected during a given day.
     * @param numOfDailyRejectedRequestedExchanges The number of trade offers that the agent requested and got rejected during a given day.
     * @param numOfDailyAcceptedRequestedExchanges The number of trade offers that the agent requested and got accepted during a given day.
     * @param numOfDailyAcceptedReceivedExchangesWithSocialCapita The number of trade offers that the agent received and accepted during a given day that involved social capita.
     * @param numOfDailyAcceptedReceivedExchangesWithoutSocialCapita The number of trade offers that the agent received and accepted during a given day that did not involve social capita.
     * @param currentSocialCapitaBalance The agent's total social capita at the end of a given day.
     */
    public void appendAgentData(
            int currentSimulationRun,
            int currentDay,
            AgentStrategyType agentStrategyType,
            double currentSatisfaction,
            int numOfDailyRejectedReceivedExchanges,
            int numOfDailyRejectedRequestedExchanges,
            int numOfDailyAcceptedRequestedExchanges,
            int numOfDailyAcceptedReceivedExchangesWithSocialCapita,
            int numOfDailyAcceptedReceivedExchangesWithoutSocialCapita,
            int currentSocialCapitaBalance
    ) {
        if (agentDataCSVWriter != null) {
            try {
                // TODO: Cite Arena code
                this.agentDataCSVWriter.append(String.valueOf(currentSimulationRun)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(currentDay)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(agentStrategyType)).append(","); // TODO: could be an issue with the scripts expecting an int
                this.agentDataCSVWriter.append(String.valueOf(currentSatisfaction)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyRejectedReceivedExchanges)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyAcceptedReceivedExchangesWithSocialCapita + numOfDailyAcceptedReceivedExchangesWithoutSocialCapita)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyRejectedRequestedExchanges)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyAcceptedRequestedExchanges)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyAcceptedReceivedExchangesWithSocialCapita)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(numOfDailyAcceptedReceivedExchangesWithoutSocialCapita)).append(",");
                this.agentDataCSVWriter.append(String.valueOf(currentSocialCapitaBalance)).append("\n");
            } catch (IOException e) {
                System.err.println("Error while trying to append data to the agent data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

    /**
     * Append a record to the daily data file.
     *
     * @param currentSimulationRun The number of a given simulation run in a simulation set.
     * @param currentDay The number of a given day in a simulation run.
     * @param socialPopulationCount The number of Household agents with a social strategy type at the end of a given day.
     * @param selfishPopulationCount The number of Household agents with a selfish strategy type at the end of a given day.
     * @param averageSocialSatisfaction The average satisfaction of all social Household agents.
     * @param averageSelfishSatisfaction The average satisfaction of all selfish Household agents.
     * @param averageSocialSatisfactionStandardDeviation The standard deviation of the average satisfaction of the social Household agent population.
     * @param averageSelfishSatisfactionStandardDeviation The standard deviation of the average satisfaction of the selfish Household agent population.
     * @param socialStatisticalValues A wrapper object containing statistical values of the social Household agent population.
     * @param selfishStatisticalValues A wrapper object containing statistical values of the selfish Household agent population.
     * @param initialRandomAllocationAverageSatisfaction The average satisfaction of the whole agent population regarding the timeslots that were initially allocated to them at the start of the day.
     * @param optimumAveragePossibleSatisfaction The highest possible average satisfaction the agent population could achieve during a given day, based on the initial timeslot allocations.
     */
    public void appendDailyData(
            int currentSimulationRun,
            int currentDay,
            int socialPopulationCount,
            int selfishPopulationCount,
            double averageSocialSatisfaction,
            double averageSelfishSatisfaction,
            double averageSocialSatisfactionStandardDeviation,
            double averageSelfishSatisfactionStandardDeviation,
            AgentStatisticalValuesPerStrategyType socialStatisticalValues,
            AgentStatisticalValuesPerStrategyType selfishStatisticalValues,
            double initialRandomAllocationAverageSatisfaction,
            double optimumAveragePossibleSatisfaction
    ) {
        if (this.dailyDataCSVWriter != null) {
            try {
                // TODO: Cite Arena code
                this.dailyDataCSVWriter.append(String.valueOf(currentSimulationRun)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(currentDay)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialPopulationCount)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishPopulationCount)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(averageSocialSatisfaction)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(averageSelfishSatisfaction)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(averageSocialSatisfactionStandardDeviation)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(averageSelfishSatisfactionStandardDeviation)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getUpperQuarter())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getUpperQuarter())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getLowerQuarter())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getLowerQuarter())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getNinetyFifthPercentile())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getNinetyFifthPercentile())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getMax())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getMax())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getMin())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getMin())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(socialStatisticalValues.getMedian())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(selfishStatisticalValues.getMedian())).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(initialRandomAllocationAverageSatisfaction)).append(",");
                this.dailyDataCSVWriter.append(String.valueOf(optimumAveragePossibleSatisfaction)).append("\n");
            } catch (IOException e) {
                System.err.println("Error while trying to append data to the day data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

    /**
     * Append a record to the exchange data file.
     *
     * @param currentSimulationRun The number of a given simulation run in a simulation set.
     * @param currentDay The number of a given day in a simulation run.
     * @param currentExchangeRound The number of a given exchange round in a day.
     * @param agentStrategyType A type of Household agents participating in the exchange round.
     * @param averageSatisfactionForType The average satisfaction in the given type population of Household agents.
     */
    public void appendExchangeData(
            int currentSimulationRun,
            int currentDay,
            int currentExchangeRound,
            AgentStrategyType agentStrategyType,
            double averageSatisfactionForType
    ) {
        if (this.exchangeDataCSVWriter != null) {
            try {
                // TODO: Cite Arena code
                this.exchangeDataCSVWriter.append(String.valueOf(currentSimulationRun)).append(",");
                this.exchangeDataCSVWriter.append(String.valueOf(currentDay)).append(",");
                this.exchangeDataCSVWriter.append(String.valueOf(currentExchangeRound)).append(","); // TODO: this starts at 0 in the original code
                this.exchangeDataCSVWriter.append(String.valueOf(agentStrategyType)).append(","); // TODO: this might fail in the scripts due to expecting a numeric value
                this.exchangeDataCSVWriter.append(String.valueOf(averageSatisfactionForType)).append("\n");
            } catch (IOException e) {
                System.err.println("Error while trying to append data to the exchange data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

    /**
     * Append a record to the performance data file
     *
     * @param currentSimulationRun The number of the active simulation run in a simulation set.
     * @param currentDay The number of the active day in a simulation run.
     * @param currentExchangeRound The number of a given exchange round in a day.
     * @param agentStrategyType A type of Household agents participating in the exchange round.
     * @param isTradeOfferReceiver Whether the agent is a receiver of a trade offer or not. TODO: might need an "isTradeOfferRequester" because just because an agent isn't a receiver it doesn't automatically make it a requester
     * @param cpuTimeUsedThisExchangeRound The number of nanoseconds it took for an agent to complete the given exchange round.
     */
    public void appendPerformanceData(
            int currentSimulationRun,
            int currentDay,
            int currentExchangeRound,
            AgentStrategyType agentStrategyType,
            boolean isTradeOfferReceiver,
            long cpuTimeUsedThisExchangeRound
    ) {
        if (this.performanceDataCSVWriter != null) {
            try {
                this.performanceDataCSVWriter.append(String.valueOf(currentSimulationRun)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(currentDay)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(currentExchangeRound)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(agentStrategyType)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(isTradeOfferReceiver)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(cpuTimeUsedThisExchangeRound)).append("\n");
            } catch (IOException e) {
                System.err.println("Error while trying to append data to the performance data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

    /**
     * Append a line to the simulation summary text file.
     *
     * @param agentStrategyType The agent strategy type that achieved a takeover in a given simulation run.
     * @param numOfTypeTakeovers The number of Household agent population takeover of a given type.
     * @param fastestTakeoverRun The number of the run with the least days it took to achieve a Household agent population takeover.
     * @param slowestTakeoverRun The number of the run with the most days it took to achieve a Household agent population takeover.
     * @param medianTakeoverRun The number of the run with the median number of days it took to achieve a Household agent population takeover.
     * @param numOfTypeFinalDayDataHolders The number of data wrappers for a final day of a simulation run of a given type.
     * @param takeoverDaysSum The total number from all runs before a Household agent population takeover happened.
     * @param averageTakeoverSatisfactionsSum The sum of all average satisfactions from all runs at the time of a Household agent population takeover.
     * @param averageTakeoverSatisfactionStandardDeviationsSum The standard deviation of the average satisfaction at the time of a Household agent population takeover.
     */
    public void appendSimulationDataByTakeoverType(
            AgentStrategyType agentStrategyType,
            int numOfTypeTakeovers,
            int fastestTakeoverRun,
            int slowestTakeoverRun,
            int medianTakeoverRun,
            int numOfTypeFinalDayDataHolders,
            double takeoverDaysSum,
            double averageTakeoverSatisfactionsSum,
            double averageTakeoverSatisfactionStandardDeviationsSum
    ) {
        if (this.simulationDataTXTWriter != null) {
            try {
                switch (agentStrategyType) {
                    case SOCIAL:
                        // TODO: Cite Arena code
                        this.simulationDataTXTWriter.append("Social Takeovers: ").append(String.valueOf(numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Fastest Social: Run ").append(String.valueOf(fastestTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Slowest Social: Run ").append(String.valueOf(slowestTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Typical Social: Run ").append(String.valueOf(medianTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover Days (social): ").append(String.valueOf(takeoverDaysSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover Satisfaction (social): ").append(String.valueOf(averageTakeoverSatisfactionsSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover SD (social): ").append(String.valueOf(averageTakeoverSatisfactionStandardDeviationsSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Final Satisfaction (social): ").append(String.valueOf(averageTakeoverSatisfactionsSum / numOfTypeFinalDayDataHolders)).append("\n");
                        this.simulationDataTXTWriter.append("Average Final SD (social): ").append(String.valueOf(averageTakeoverSatisfactionStandardDeviationsSum / numOfTypeFinalDayDataHolders)).append("\n\n");

                        break;
                    case SELFISH:
                        // TODO: Cite Arena code
                        this.simulationDataTXTWriter.append("Selfish Takeovers: ").append(String.valueOf(numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Fastest selfish: Run ").append(String.valueOf(fastestTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Slowest selfish: Run ").append(String.valueOf(slowestTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Typical selfish: Run ").append(String.valueOf(medianTakeoverRun)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover Days (selfish): ").append(String.valueOf(takeoverDaysSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover Satisfaction (selfish): ").append(String.valueOf(averageTakeoverSatisfactionsSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Takeover SD (selfish): ").append(String.valueOf(averageTakeoverSatisfactionStandardDeviationsSum / numOfTypeTakeovers)).append("\n");
                        this.simulationDataTXTWriter.append("Average Final Satisfaction (selfish): ").append(String.valueOf(averageTakeoverSatisfactionsSum / numOfTypeFinalDayDataHolders)).append("\n");
                        this.simulationDataTXTWriter.append("Average Final SD (selfish): ").append(String.valueOf(averageTakeoverSatisfactionStandardDeviationsSum / numOfTypeFinalDayDataHolders));

                        break;
                    default:
                        System.err.println("Tried to append undefined exchange type data.");

                        break;
                }
            } catch (IOException e) {
                System.err.println("Error while trying to append social run data to the simulation data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

    /**
     * Flush the data of all file writers.
     */
    public void flushAllDataWriters() {
        try {
            this.simulationDataTXTWriter.flush();
            this.agentDataCSVWriter.flush();
            this.dailyDataCSVWriter.flush();
            this.exchangeDataCSVWriter.flush();
            this.performanceDataCSVWriter.flush();
        } catch (IOException e) {
            System.err.println("Error while trying to flush the data writers.");
        }
    }

    /**
     * Close all file writers.
     */
    public void closeAllDataWriters() {
        try {
            this.simulationDataTXTWriter.close();
            this.agentDataCSVWriter.close();
            this.dailyDataCSVWriter.close();
            this.exchangeDataCSVWriter.close();
            this.performanceDataCSVWriter.close();
        } catch (IOException e) {
            System.err.println("Error while trying to close the data writers.");
        }
    }

    /**
     * Convert the agent strategy type enum into a capitalised string.
     *
     * @param agentStrategyType The given strategy type of an agent.
     * @return (String) The capitalised text of the enum. E.g. SELFISH -> "Selfish"
     */
    private String getAgentStrategyTypeCapString(AgentStrategyType agentStrategyType) {
        return agentStrategyType.toString().substring(0, 1).toUpperCase() + agentStrategyType.toString().substring(1).toLowerCase();
    }
}