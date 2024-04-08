package com.napier.performancedata;

import com.napier.arena.singletons.SimulationConfigurationSingleton;
import com.napier.arena.types.AgentStrategyType;
import com.napier.arena.types.ExchangeType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Scanner;

public class PerformanceDataProcessor {
    public static void main(String[] args) {
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        String simulationDataOutputFolderPath = getSimulationDataOutputFolderPath(
                config.doesUtiliseSocialCapita(),
                config.doesUtiliseSingleAgentType(),
                config.getSelectedSingleAgentType(),
                config.getExchangeType()
        );

        File exchangeDataFile = new File(simulationDataOutputFolderPath, "exchangeData.csv");

        long averageHouseholdCPUTimeSocialSum = 0L;
        long averageRequesterCPUTimeSocialSum = 0L;
        long averageReceiverCPUTimeSocialSum = 0L;
        long averageNonParticipantCPUTimeSocialSum = 0L;
        int numOfAverageHouseholdCPUTimeSocialNonNullEntries = 0;
        int numOfAverageRequesterCPUTimeSocialNonNullEntries = 0;
        int numOfAverageReceiverCPUTimeSocialNonNullEntries = 0;
        int numOfAverageNonParticipantCPUTimeSocialNonNullEntries = 0;

        long averageHouseholdCPUTimeSelfishSum = 0L;
        long averageRequesterCPUTimeSelfishSum = 0L;
        long averageReceiverCPUTimeSelfishSum = 0L;
        long averageNonParticipantCPUTimeSelfishSum = 0L;
        int numOfAverageHouseholdCPUTimeSelfishNonNullEntries = 0;
        int numOfAverageRequesterCPUTimeSelfishNonNullEntries = 0;
        int numOfAverageReceiverCPUTimeSelfishNonNullEntries = 0;
        int numOfAverageNonParticipantCPUTimeSelfishNonNullEntries = 0;

        try (Scanner scanner = new Scanner(exchangeDataFile)) {
            while(scanner.hasNextLine()) {
                String[] record = scanner.nextLine().split(",");
                String strategyType = record[3];

                if (strategyType.equals("SOCIAL")) {
                    if (!record[5].equals("NaN")) {
                        averageHouseholdCPUTimeSocialSum += new BigDecimal(record[5]).longValue();
                        numOfAverageHouseholdCPUTimeSocialNonNullEntries++;
                    }

                    if (!record[6].equals("NaN")) {
                        averageRequesterCPUTimeSocialSum += new BigDecimal(record[6]).longValue();
                        numOfAverageRequesterCPUTimeSocialNonNullEntries++;
                    }

                    if (!record[7].equals("NaN")) {
                        averageReceiverCPUTimeSocialSum += new BigDecimal(record[7]).longValue();
                        numOfAverageReceiverCPUTimeSocialNonNullEntries++;
                    }

                    if (!record[8].equals("NaN")) {
                        averageNonParticipantCPUTimeSocialSum += new BigDecimal(record[8]).longValue();
                        numOfAverageNonParticipantCPUTimeSocialNonNullEntries++;
                    }
                } else if (strategyType.equals("SELFISH")) {
                    if (!record[5].equals("NaN")) {
                        averageHouseholdCPUTimeSelfishSum += new BigDecimal(record[5]).longValue();
                        numOfAverageHouseholdCPUTimeSelfishNonNullEntries++;
                    }

                    if (!record[6].equals("NaN")) {
                        averageRequesterCPUTimeSelfishSum += new BigDecimal(record[6]).longValue();
                        numOfAverageRequesterCPUTimeSelfishNonNullEntries++;
                    }

                    if (!record[7].equals("NaN")) {
                        averageReceiverCPUTimeSelfishSum += new BigDecimal(record[7]).longValue();
                        numOfAverageReceiverCPUTimeSelfishNonNullEntries++;
                    }

                    if (!record[8].equals("NaN")) {
                        averageNonParticipantCPUTimeSelfishSum += new BigDecimal(record[8]).longValue();
                        numOfAverageNonParticipantCPUTimeSelfishNonNullEntries++;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        File performanceDataFile = new File(simulationDataOutputFolderPath, "performanceData.csv");

        try (FileWriter performanceDataFileWriter = new FileWriter(performanceDataFile)) {
            performanceDataFileWriter.append("Agent type,");
            performanceDataFileWriter.append("Average Household Exchange Round Time (ns),");
            performanceDataFileWriter.append("Average Requester Exchange Round Time (ns),");
            performanceDataFileWriter.append("Average Receiver Exchange Round Time (ns),");
            performanceDataFileWriter.append("Average Non Participant Exchange Round Time (ns)").append("\n");

            for (AgentStrategyType agentStrategyType : AgentStrategyType.values()) {
                performanceDataFileWriter.append(String.valueOf(agentStrategyType)).append(",");

                switch (agentStrategyType) {
                    case SOCIAL:
                        performanceDataFileWriter.append(String.valueOf(averageHouseholdCPUTimeSocialSum / numOfAverageHouseholdCPUTimeSocialNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageRequesterCPUTimeSocialSum / numOfAverageRequesterCPUTimeSocialNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageReceiverCPUTimeSocialSum / numOfAverageReceiverCPUTimeSocialNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageNonParticipantCPUTimeSocialSum / numOfAverageNonParticipantCPUTimeSocialNonNullEntries));

                        break;
                    case SELFISH:
                        performanceDataFileWriter.append(String.valueOf(averageHouseholdCPUTimeSelfishSum / numOfAverageHouseholdCPUTimeSelfishNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageRequesterCPUTimeSelfishSum / numOfAverageRequesterCPUTimeSelfishNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageReceiverCPUTimeSelfishSum / numOfAverageReceiverCPUTimeSelfishNonNullEntries)).append(",");
                        performanceDataFileWriter.append(String.valueOf(averageNonParticipantCPUTimeSelfishSum / numOfAverageNonParticipantCPUTimeSelfishNonNullEntries)).append("\n");

                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getSimulationDataOutputFolderPath(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType singleAgentTypeUsed, ExchangeType exchangeTypeUsed) {
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        // Create a directory to store the data output by all simulations being run.
        String simulationDataOutputFolderPath = config.getResultsFolderPath() + "/" + config.getStartingSeed() + "/useSC_" + doesUtiliseSocialCapita + "_AType_";

        // Append the agent types used to the folder path
        if (!doesUtiliseSingleAgentType) {
            simulationDataOutputFolderPath += "mixed";
        } else {
            simulationDataOutputFolderPath += getAgentStrategyTypeCapString(singleAgentTypeUsed);
        }

        simulationDataOutputFolderPath += "_EType_" + exchangeTypeUsed + "/data";

        return simulationDataOutputFolderPath;
    }

    /**
     * Convert the agent strategy type enum into a capitalised string.
     *
     * @param agentStrategyType The given strategy type of an agent.
     * @return (String) The capitalised text of the enum. E.g. SELFISH -> "Selfish"
     */
    private static String getAgentStrategyTypeCapString(AgentStrategyType agentStrategyType) {
        return agentStrategyType.toString().substring(0, 1).toUpperCase() + agentStrategyType.toString().substring(1).toLowerCase();
    }
}
