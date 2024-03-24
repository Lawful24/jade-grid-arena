package com.napier.singletons;

import com.napier.types.AgentStrategyType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimulationDataOutputSingleton {
    private static SimulationDataOutputSingleton instance;
    String simulationDataOutputParentFolder;
    String simulationDataOutputFolder;

    public static SimulationDataOutputSingleton getInstance() {
        if (instance == null) {
            instance = new SimulationDataOutputSingleton();
        }

        return instance;
    }

    public SimulationDataOutputSingleton() {

    }

    public void prepareSimulationDataOutput(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        this.createSimulationResultsFolderTree(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
        this.createAgentDataOutputFile();
        this.createExchangeDataOutputFile();
        this.createDailyDataOutputFile();
        this.createSimulationDataOutputFile(doesUtiliseSocialCapita, doesUtiliseSingleAgentType, selectedSingleAgentType);
    }

    private void createSimulationResultsFolderTree(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

        // TODO: Cite Arena code
        // Create a directory to store the data output by all simulations being run.
        this.simulationDataOutputParentFolder = config.getResultsFolderPath() + config.getSeed() + "/useSC_" + doesUtiliseSocialCapita + "_AType_";

        if (!doesUtiliseSingleAgentType) {
            this.simulationDataOutputParentFolder += "mixed";
        } else {
            this.simulationDataOutputParentFolder += this.getAgentStrategyTypeCapString(selectedSingleAgentType);
        }

        this.simulationDataOutputFolder = this.simulationDataOutputParentFolder + "/data";

        try {
            // TODO: Cite Arena code
            // Create a directory to store the data output by the simulation.
            Files.createDirectories(Path.of(this.simulationDataOutputFolder));
        } catch (IOException e) {
            System.err.println("Error while trying to create the folder to store the results of the simulation in: " + e.getMessage());
        }
    }

    private void createAgentDataOutputFile() {
        if (this.simulationDataOutputFolder != null) {
            // TODO: Cite Arena code
            File allAgentData = new File(this.simulationDataOutputFolder, "agentData.csv");

            try (FileWriter perAgentDataCSVWriter = new FileWriter(allAgentData)) {
                perAgentDataCSVWriter.append("Simulation Run,");
                perAgentDataCSVWriter.append("Day,");
                perAgentDataCSVWriter.append("Agent Type,");
                perAgentDataCSVWriter.append("Satisfaction,");
                perAgentDataCSVWriter.append("Rejected Received Exchanges,");
                perAgentDataCSVWriter.append("Accepted Received Exchanges,");
                perAgentDataCSVWriter.append("Rejected Requested Exchanges,");
                perAgentDataCSVWriter.append("Accepted Requested Exchanges,");
                perAgentDataCSVWriter.append("Social Capital Exchanges,");
                perAgentDataCSVWriter.append("No Social Capital Exchanges,");
                perAgentDataCSVWriter.append("Unspent Social Capital");
                perAgentDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in agent data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    private void createDailyDataOutputFile() {
        if (this.simulationDataOutputFolder != null) {
            // TODO: Cite Arena code
            File allDailyData = new File(this.simulationDataOutputFolder, "dailyData.csv");

            try (FileWriter allDailyDataCSVWriter = new FileWriter(allDailyData)) {
                allDailyDataCSVWriter.append("Simulation Run,");
                allDailyDataCSVWriter.append("Day,");
                allDailyDataCSVWriter.append("Social Pop,");
                allDailyDataCSVWriter.append("Selfish Pop,");
                allDailyDataCSVWriter.append("Social Sat,");
                allDailyDataCSVWriter.append("Selfish Sat,");
                allDailyDataCSVWriter.append("Social SD,");
                allDailyDataCSVWriter.append("Selfish SD,");
                allDailyDataCSVWriter.append("Social Upper Quartile,");
                allDailyDataCSVWriter.append("Selfish Upper Quartile,");
                allDailyDataCSVWriter.append("Social Lower Quartile,");
                allDailyDataCSVWriter.append("Selfish Lower Quartile,");
                allDailyDataCSVWriter.append("Social 95th Percentile,");
                allDailyDataCSVWriter.append("Selfish 95th Percentile,");
                allDailyDataCSVWriter.append("Social Max,");
                allDailyDataCSVWriter.append("Selfish Max,");
                allDailyDataCSVWriter.append("Social Min,");
                allDailyDataCSVWriter.append("Selfish Min,");
                allDailyDataCSVWriter.append("Social Median,");
                allDailyDataCSVWriter.append("Selfish Median,");
                allDailyDataCSVWriter.append("Random Allocation Sat,");
                allDailyDataCSVWriter.append("Optimum Allocation Sat");
                allDailyDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in daily data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    private void createExchangeDataOutputFile() {
        if (this.simulationDataOutputFolder != null) {
            // TODO: Cite Arena code
            File exchangeData = new File(this.simulationDataOutputFolder, "exchangeData.csv");

            try (FileWriter eachRoundDataCSVWriter = new FileWriter(exchangeData)) {
                eachRoundDataCSVWriter.append("Simulation Run,");
                eachRoundDataCSVWriter.append("Day,");
                eachRoundDataCSVWriter.append("Round,");
                eachRoundDataCSVWriter.append("Agent Type,");
                eachRoundDataCSVWriter.append("Satisfaction");
                eachRoundDataCSVWriter.append("\n");
            } catch (IOException e) {
                System.err.println("Could not write in exchange data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    private void createSimulationDataOutputFile(boolean doesUtiliseSocialCapita, boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

        if (this.simulationDataOutputParentFolder != null) {
            // TODO: Cite Arena code
            File simulationData = new File(this.simulationDataOutputParentFolder, "simulationData.txt");

            try (FileWriter simulationDataWriter = new FileWriter(simulationData)) {
                simulationDataWriter.append("Simulation Information: \n\n");
                simulationDataWriter.append("Seed: ").append(String.valueOf(config.getSeed())).append("\n");
                simulationDataWriter.append("Single agent type: ").append(String.valueOf(doesUtiliseSingleAgentType)).append("\n");

                if (doesUtiliseSingleAgentType) {
                    simulationDataWriter.append("Agent type: ").append(String.valueOf(selectedSingleAgentType)).append("\n");
                }

                simulationDataWriter.append("Use social capital: ").append(String.valueOf(doesUtiliseSocialCapita)).append("\n");
                simulationDataWriter.append("Simulation runs: ").append(String.valueOf(config.getNumOfSimulationRuns())).append("\n");
                simulationDataWriter.append("Days after strategy takeover: ").append(String.valueOf(config.getNumOfAdditionalDaysAfterTakeover())).append("\n");
                simulationDataWriter.append("Population size: ").append(String.valueOf(config.getPopulationCount())).append("\n");
                simulationDataWriter.append("Unique time-slots: ").append(String.valueOf(config.getNumOfUniqueTimeSlots())).append("\n");
                simulationDataWriter.append("Slots per agent: ").append(String.valueOf(config.getNumOfSlotsPerAgent())).append("\n");
                simulationDataWriter.append("Number of agents to evolve: ").append(String.valueOf(config.getNumOfAgentsToEvolve())).append("\n");
                simulationDataWriter.append("Starting ratio of agent types: ")
                        .append(this.getAgentStrategyTypeCapString(AgentStrategyType.SELFISH))
                        .append(" ")
                        .append(String.valueOf(config.getSelfishPopulationCount()))
                        .append(" : ")
                        .append(String.valueOf(config.getPopulationCount() - config.getSelfishPopulationCount()))
                        .append(" ")
                        .append(this.getAgentStrategyTypeCapString(AgentStrategyType.SOCIAL)); // TODO: this might have to be reworked but definitely has to be tested
                simulationDataWriter.append("\n\n");
            } catch (IOException e) {
                System.err.println("Could not write in exchange data output file.");
            }
        } else {
            System.err.println("Data writer tried writing to file without knowing the path of its parent folder.");
        }
    }

    private String getAgentStrategyTypeCapString(AgentStrategyType agentStrategyType) {
        return agentStrategyType.toString().substring(0, 1).toUpperCase() + agentStrategyType.toString().substring(1).toLowerCase();
    }
}
