package com.napier;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class RunConfiguration {
    private long seed; // seed
    private String resultsFolderPath; // results.folder (folderName)
    private String pythonExePath; // python.executable (pythonExe)
    private String pythonScriptsPath; // python.scripts (pythonPath)
    private int populationSize; // population.size (populationSize)
    private int numOfSlotsPerAgent; // agent.time-slots (slotsPerAgent)
    private int numOfUniqueTimeSlots; // simulation.uniqueTime-slots (uniqueTimeSlots)
    // unused config variable: simulation.additionalDays
    private int numOfSimulationRuns; // simulation.runs (simulationRuns)
    private boolean isSingleAgentTypeUsed; // agent.singleType (singleAgentType)
    private AgentStrategy selectedSingleAgentType; // agent.selectedSingleType (selectedSingleType)
    private boolean doesUtiliseSocialCapital; // agent.useSocialCapital (socialCapital)
    private double beta; // agent.beta (β)
    private int comparisonLevel; // simulation.comparisonLevel (COMPARISON_LEVEL)
    private double[][] demandCurves; // demand.curves (demandCurves)
    private int[] availabilityCurve; // availability.curve (availabilityCurves)
    private int percentageOfAgentsToEvolve; // agents.evolvePercentage (numberOfAgentsToEvolve)
    private AgentStrategy[] agentTypes; // agent.typeRatio (could be a tuple then converted to an int array) (agentTypes)
    private double[] satisfactionCurve; // agent.satisfactionCurve (satisfactionCurve)

    public RunConfiguration(boolean isDebug) {
        // Retrieve user parameters from the config file.
        Properties properties = new Properties();
        loadPropertiesFromFile(properties, isDebug);

        this.seed = Long.parseLong(properties.getProperty("seed"));
        this.resultsFolderPath = properties.getProperty("results.folder");
        this.pythonExePath = properties.getProperty("python.executable");
        this.pythonScriptsPath = properties.getProperty("python.scripts");
        this.populationSize = Integer.parseInt(properties.getProperty("population.size"));
        this.numOfSlotsPerAgent = Integer.parseInt(properties.getProperty("agent.time-slots"));
        this.numOfUniqueTimeSlots = Integer.parseInt(properties.getProperty("simulation.uniqueTime-slots"));
        // additional days?
        this.numOfSimulationRuns = Integer.parseInt(properties.getProperty("simulation.runs"));
        this.isSingleAgentTypeUsed = Boolean.parseBoolean(properties.getProperty("agent.singleType"));
        this.selectedSingleAgentType = inputToStrategyEnum(properties.getProperty("agent.selectedSingleType"));
        this.doesUtiliseSocialCapital = Boolean.parseBoolean(properties.getProperty("agent.useSocialCapital"));
        this.beta = Integer.parseInt(properties.getProperty("agent.beta"));
        this.comparisonLevel = Integer.parseInt(properties.getProperty("simulation.comparisonLevel"));
        this.demandCurves = inputToDouble2DArray(properties.getProperty("demand.curves"));
        this.availabilityCurve = inputToIntArray(properties.getProperty("availability.curve"));
        this.percentageOfAgentsToEvolve = Integer.parseInt(properties.getProperty("agents.evolvePercentage"));
        this.agentTypes = ratioToAgentTypeArray(properties.getProperty("agent.typeRatio"));
        this.satisfactionCurve = inputToDoubleArray(properties.getProperty("agent.satisfactionCurve"));
    }

    /* Accessors */

    public long getSeed() {
        return seed;
    }

    public String getResultsFolderPath() {
        return resultsFolderPath;
    }

    public String getPythonExePath() {
        return pythonExePath;
    }

    public String getPythonScriptsPath() {
        return pythonScriptsPath;
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public int getNumOfSlotsPerAgent() {
        return numOfSlotsPerAgent;
    }

    public int getNumOfUniqueTimeSlots() {
        return numOfUniqueTimeSlots;
    }

    public int getNumOfSimulationRuns() {
        return numOfSimulationRuns;
    }

    public boolean isSingleAgentTypeUsed() {
        return isSingleAgentTypeUsed;
    }

    public AgentStrategy getSelectedSingleAgentType() {
        return selectedSingleAgentType;
    }

    public boolean isDoesUtiliseSocialCapital() {
        return doesUtiliseSocialCapital;
    }

    public double getBeta() {
        return beta;
    }

    public int getComparisonLevel() {
        return comparisonLevel;
    }

    public double[][] getDemandCurves() {
        return demandCurves;
    }

    public int[] getAvailabilityCurve() {
        return availabilityCurve;
    }

    public int getPercentageOfAgentsToEvolve() {
        return percentageOfAgentsToEvolve;
    }

    public AgentStrategy[] getAgentTypes() {
        return agentTypes;
    }

    public double[] getSatisfactionCurve() {
        return satisfactionCurve;
    }

    /* Mutators */

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void setResultsFolderPath(String resultsFolderPath) {
        this.resultsFolderPath = resultsFolderPath;
    }

    public void setPythonExePath(String pythonExePath) {
        this.pythonExePath = pythonExePath;
    }

    public void setPythonScriptsPath(String pythonScriptsPath) {
        this.pythonScriptsPath = pythonScriptsPath;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public void setNumOfSlotsPerAgent(int numOfSlotsPerAgent) {
        this.numOfSlotsPerAgent = numOfSlotsPerAgent;
    }

    public void setNumOfUniqueTimeSlots(int numOfUniqueTimeSlots) {
        this.numOfUniqueTimeSlots = numOfUniqueTimeSlots;
    }

    public void setNumOfSimulationRuns(int numOfSimulationRuns) {
        this.numOfSimulationRuns = numOfSimulationRuns;
    }

    public void setSingleAgentTypeUsed(boolean singleAgentTypeUsed) {
        isSingleAgentTypeUsed = singleAgentTypeUsed;
    }

    public void setSelectedSingleAgentType(AgentStrategy selectedSingleAgentType) {
        this.selectedSingleAgentType = selectedSingleAgentType;
    }

    public void setDoesUtiliseSocialCapital(boolean doesUtiliseSocialCapital) {
        this.doesUtiliseSocialCapital = doesUtiliseSocialCapital;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public void setComparisonLevel(int comparisonLevel) {
        this.comparisonLevel = comparisonLevel;
    }

    public void setDemandCurves(double[][] demandCurves) {
        this.demandCurves = demandCurves;
    }

    public void setAvailabilityCurve(int[] availabilityCurve) {
        this.availabilityCurve = availabilityCurve;
    }

    public void setPercentageOfAgentsToEvolve(int percentageOfAgentsToEvolve) {
        this.percentageOfAgentsToEvolve = percentageOfAgentsToEvolve;
    }

    public void setAgentTypes(AgentStrategy[] agentTypes) {
        this.agentTypes = agentTypes;
    }

    public void setSatisfactionCurve(double[] satisfactionCurve) {
        this.satisfactionCurve = satisfactionCurve;
    }

    /* Helpers */

    private void loadPropertiesFromFile(Properties properties, boolean isDebug) {
        String configFilename;

        if (isDebug) {
            configFilename = "debug.config.properties";
        } else {
            configFilename = "config.properties";
        }

        try (InputStream input = new FileInputStream(configFilename)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    private double[] inputToDoubleArray(String input) {
        return Arrays.stream(input.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    // TODO: Cite Arena code
    private double[][] inputToDouble2DArray(String input) {
        // Split the input string into sets using "||" as the delimiter
        String[] sets = input.split("\\|\\|");

        // Initialize a 2D double array to store the result
        double[][] result = new double[sets.length][];

        for (int i = 0; i < sets.length; i++) {
            // Split each set by comma and convert it to a double array
            String[] numberStrings = sets[i].split(",");
            result[i] = new double[numberStrings.length];

            for (int j = 0; j < numberStrings.length; j++) {
                // Parse each element into a double
                result[i][j] = Double.parseDouble(numberStrings[j]);
            }
        }

        return result;
    }

    private int[] inputToIntArray(String input) {
        return Arrays.stream(input.split(",")).mapToInt(Integer::parseInt).toArray();
    }

    // TODO: Flag as nullable
    private AgentStrategy inputToStrategyEnum(String input) {
        return input.equals("social") ? AgentStrategy.SOCIAL : input.equals("selfish") ? AgentStrategy.SELFISH : null;
    }

    // TODO: Cite Arena code
    private AgentStrategy[] ratioToAgentTypeArray(String input) {
        // Split the input string by colon
        String[] ratioParts = input.split(":");
        int a = Integer.parseInt(ratioParts[0]);
        int b = Integer.parseInt(ratioParts[1]);

        AgentStrategy[] result = new AgentStrategy[a + b];

        for (int i = 0; i < a; i++) {
            result[i] = AgentStrategy.SELFISH;
        }

        for (int i = a; i < a + b; i++) {
            result[i] = AgentStrategy.SOCIAL;
        }

        return result;
    }
}
