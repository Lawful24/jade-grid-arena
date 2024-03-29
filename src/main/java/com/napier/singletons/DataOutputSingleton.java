package com.napier.singletons;

import com.napier.concepts.AgentStatisticalValuesPerStrategyType;
import com.napier.types.AgentStrategyType;
import com.napier.types.ExchangeType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataOutputSingleton {
    private static DataOutputSingleton instance;
    String simulationDataOutputParentFolderPath;
    String simulationDataOutputFolderPath;
    File simulationDataFile;
    File agentDataFile;
    File dailyDataFile;
    File exchangeDataFile;
    File performanceDataFile;
    FileWriter simulationDataTXTWriter;
    FileWriter agentDataCSVWriter;
    FileWriter dailyDataCSVWriter;
    FileWriter exchangeDataCSVWriter;
    FileWriter performanceDataCSVWriter;

    public static DataOutputSingleton getInstance() {
        if (instance == null) {
            instance = new DataOutputSingleton();
        }

        return instance;
    }

    public DataOutputSingleton() {
        // no-op
    }

    public void prepareSimulationDataOutput(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        // TODO: take the exchange type into account for the folder creation
        this.createSimulationResultsFolderTree(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
        this.createAgentDataOutputFile();
        this.createExchangeDataOutputFile();
        this.createDailyDataOutputFile();
        this.createPerformanceDataOutputFile();
        this.createSimulationDataOutputFile(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
    }

    private void createSimulationResultsFolderTree(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        // TODO: Cite Arena code
        // Create a directory to store the data output by all simulations being run.
        this.simulationDataOutputParentFolderPath = config.getResultsFolderPath() + "/" + config.getStartingSeed() + "/useSC_" + doesUtiliseSocialCapita + "_AType_";

        if (!doesUtiliseSingleAgentType) {
            this.simulationDataOutputParentFolderPath += "mixed";
        } else {
            this.simulationDataOutputParentFolderPath += this.getAgentStrategyTypeCapString(selectedSingleAgentType);
        }

        this.simulationDataOutputParentFolderPath += "_EType_" + config.getExchangeType();

        this.simulationDataOutputFolderPath = this.simulationDataOutputParentFolderPath + "/data";

        try {
            // TODO: Cite Arena code
            // Create a directory to store the data output by the simulation.
            Files.createDirectories(Path.of(this.simulationDataOutputFolderPath));
        } catch (IOException e) {
            System.err.println("Error while trying to create the folder to store the results of the simulation in: " + e.getMessage());
        }
    }

    private void createAgentDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            this.agentDataFile = new File(this.simulationDataOutputFolderPath, "agentData.csv");

            try {
                this.agentDataCSVWriter = new FileWriter(this.agentDataFile);

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

    private void createDailyDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            this.dailyDataFile = new File(this.simulationDataOutputFolderPath, "dailyData.csv");

            try {
                this.dailyDataCSVWriter = new FileWriter(this.dailyDataFile);

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

    private void createExchangeDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            // TODO: Cite Arena code
            this.exchangeDataFile = new File(this.simulationDataOutputFolderPath, "exchangeData.csv");

            try {
                this.exchangeDataCSVWriter = new FileWriter(this.exchangeDataFile);

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

    private void createPerformanceDataOutputFile() {
        if (this.simulationDataOutputFolderPath != null) {
            this.performanceDataFile = new File(this.simulationDataOutputFolderPath, "performanceData.csv");

            try {
                this.performanceDataCSVWriter = new FileWriter(this.performanceDataFile);

                performanceDataCSVWriter.append("Simulation Run,");
                performanceDataCSVWriter.append("Day,");
                performanceDataCSVWriter.append("Round,");
                performanceDataCSVWriter.append("Name,"); // TODO: might not be necessary
                performanceDataCSVWriter.append("Strategy Type,");
                performanceDataCSVWriter.append("Requester/Receiver,");
                performanceDataCSVWriter.append("CPU Time Used");
                performanceDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in performance data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    private void createSimulationDataOutputFile(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        if (this.simulationDataOutputParentFolderPath != null) {
            // TODO: Cite Arena code
            this.simulationDataFile = new File(this.simulationDataOutputParentFolderPath, "simulationData.txt");

            try {
                this.simulationDataTXTWriter = new FileWriter(this.simulationDataFile);

                this.simulationDataTXTWriter.append("Simulation Information: \n\n");
                this.simulationDataTXTWriter.append("Seed: ").append(String.valueOf(config.getCurrentSeed())).append("\n");
                this.simulationDataTXTWriter.append("Single agent type: ").append(String.valueOf(doesUtiliseSingleAgentType)).append("\n");

                if (doesUtiliseSingleAgentType) {
                    this.simulationDataTXTWriter.append("Agent type: ").append(String.valueOf(selectedSingleAgentType)).append("\n");
                }

                this.simulationDataTXTWriter.append("Use social capita: ").append(String.valueOf(doesUtiliseSocialCapita)).append("\n");
                this.simulationDataTXTWriter.append("Simulation runs: ").append(String.valueOf(config.getNumOfSimulationRuns())).append("\n");
                this.simulationDataTXTWriter.append("Days after strategy takeover: ").append(String.valueOf(config.getNumOfAdditionalDaysAfterTakeover())).append("\n");
                this.simulationDataTXTWriter.append("Population size: ").append(String.valueOf(config.getPopulationCount())).append("\n");
                this.simulationDataTXTWriter.append("Unique time-slots: ").append(String.valueOf(config.getNumOfUniqueTimeSlots())).append("\n");
                this.simulationDataTXTWriter.append("Slots per agent: ").append(String.valueOf(config.getNumOfSlotsPerAgent())).append("\n");
                this.simulationDataTXTWriter.append("Number of agents to evolve: ").append(String.valueOf(config.getNumOfAgentsToEvolve())).append("\n");
                this.simulationDataTXTWriter.append("Starting ratio of agent types: ")
                        .append(this.getAgentStrategyTypeCapString(AgentStrategyType.SELFISH))
                        .append(" ")
                        .append(String.valueOf(config.getSelfishPopulationCount()))
                        .append(" : ")
                        .append(String.valueOf(config.getPopulationCount() - config.getSelfishPopulationCount()))
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

    // TODO: call this for each agent type at the end of the exchange round
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

    public void appendPerformanceData(
            int currentSimulationRun,
            int currentDay,
            int currentExchangeRound,
            String agentNickname,
            AgentStrategyType agentStrategyType,
            boolean isTradeOfferReceiver,
            long cpuTimeUsedThisExchangeRound
    ) {
        if (this.performanceDataCSVWriter != null) {
            try {
                this.performanceDataCSVWriter.append(String.valueOf(currentSimulationRun)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(currentDay)).append(",");
                this.performanceDataCSVWriter.append(String.valueOf(currentExchangeRound)).append(",");
                this.performanceDataCSVWriter.append(agentNickname).append(","); // TODO: might not be necessary
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

    public void appendSimulationDataForSocialRuns(
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
                        // TODO
                        break;
                }
            } catch (IOException e) {
                System.err.println("Error while trying to append social run data to the simulation data file.");
            }
        } else {
            System.err.println("Tried to write data output file but the FileWriter was null.");
        }
    }

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

    private String getAgentStrategyTypeCapString(AgentStrategyType agentStrategyType) {
        return agentStrategyType.toString().substring(0, 1).toUpperCase() + agentStrategyType.toString().substring(1).toLowerCase();
    }
}
