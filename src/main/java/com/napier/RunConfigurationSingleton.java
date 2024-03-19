package com.napier;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class RunConfigurationSingleton {
    private static RunConfigurationSingleton instance;
    private static final Random random = new Random();

    /* Configuration Properties */
    private final long seed; // seed
    private final String resultsFolderPath; // results.folder (folderName)
    private final String pythonExePath; // python.executable (pythonExe)
    private final String pythonScriptsPath; // python.scripts (pythonPath)
    private final int populationCount; // population.size (populationSize)
    private final int numOfSlotsPerAgent; // agent.time-slots (slotsPerAgent)
    private final int numOfUniqueTimeSlots; // simulation.uniqueTime-slots (uniqueTimeSlots)
    private final int additionalDays; // simulation.additionalDays (days)
    private final int numOfSimulationRuns; // simulation.runs (simulationRuns)
    private final boolean isSingleAgentTypeUsed; // agent.singleType (singleAgentType)
    private final AgentStrategyType selectedSingleAgentType; // agent.selectedSingleType (selectedSingleType)
    private final boolean doesUtiliseSocialCapital; // agent.useSocialCapital (socialCapital)
    private final double beta; // agent.beta (β)
    private final int comparisonLevel; // simulation.comparisonLevel (COMPARISON_LEVEL)
    private final double[][] demandCurves; // demand.curves (demandCurves)
    private final int[] availabilityCurve; // availability.curve (availabilityCurves)
    private final int selfishPopulationCount; // based on agent.typeRatio (agentTypes)
    private final double[] satisfactionCurve; // agent.satisfactionCurve (satisfactionCurve)

    /* Calculated Values */
    private final double[][] bucketedDemandCurves;
    private final double[] totalDemandValues;
    private ArrayList<Integer> demandCurveIndices;
    private final int[] bucketedAvailabilityCurve;
    private final int totalAvailableEnergy;
    private final int numOfAgentsToEvolve; // agents.evolvePercentage (numberOfAgentsToEvolve)

    public static RunConfigurationSingleton getInstance() {
        if (instance == null) {
            instance = new RunConfigurationSingleton(true);
        }

        return instance;
    }

    private RunConfigurationSingleton(boolean debugMode) {
        // Retrieve user parameters from the config file
        Properties properties = new Properties();
        loadPropertiesFromFile(properties, debugMode);

        // Read the configuration variables from the config properties and store them in the attributes
        this.seed = Long.parseLong(properties.getProperty("seed"));
        this.resultsFolderPath = properties.getProperty("results.folder");
        this.pythonExePath = properties.getProperty("python.executable");
        this.pythonScriptsPath = properties.getProperty("python.scripts");
        this.populationCount = Integer.parseInt(properties.getProperty("population.size"));
        this.numOfSlotsPerAgent = Integer.parseInt(properties.getProperty("agent.time-slots"));
        this.numOfUniqueTimeSlots = Integer.parseInt(properties.getProperty("simulation.uniqueTime-slots"));
        this.additionalDays = Integer.parseInt(properties.getProperty("simulation.additionalDays"));
        this.numOfSimulationRuns = Integer.parseInt(properties.getProperty("simulation.runs"));
        this.isSingleAgentTypeUsed = Boolean.parseBoolean(properties.getProperty("agent.singleType"));
        this.selectedSingleAgentType = inputToStrategyEnum(properties.getProperty("agent.selectedSingleType"));
        this.doesUtiliseSocialCapital = Boolean.parseBoolean(properties.getProperty("agent.useSocialCapital"));
        this.beta = Integer.parseInt(properties.getProperty("agent.beta"));
        this.comparisonLevel = Integer.parseInt(properties.getProperty("simulation.comparisonLevel"));
        this.demandCurves = inputToDouble2DArray(properties.getProperty("demand.curves"));
        this.availabilityCurve = inputToIntArray(properties.getProperty("availability.curve"));
        double evolutionPercentage = Double.parseDouble(properties.getProperty("agents.evolvePercentage"));
        this.selfishPopulationCount = ratioToSelfishPopulationCount(properties.getProperty("agent.typeRatio"));
        this.satisfactionCurve = inputToDoubleArray(properties.getProperty("agent.satisfactionCurve"));

        // Calculate values based on the configuration properties
        random.setSeed(this.seed);
        this.bucketedDemandCurves = this.bucketSortDemandCurves();
        this.totalDemandValues = this.calculateTotalDemandValues();
        this.demandCurveIndices = this.createDemandCurveIndices();
        this.bucketedAvailabilityCurve = this.bucketSortAvailabilityCurve();
        this.totalAvailableEnergy = this.calculateTotalAvailableEnergy();
        this.numOfAgentsToEvolve = this.calculateNumberOfAgentsToEvolve(evolutionPercentage);
    }

    /* Accessors */

    public Random getRandom() {
        return random;
    }

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

    public int getPopulationCount() {
        return populationCount;
    }

    public int getNumOfSlotsPerAgent() {
        return numOfSlotsPerAgent;
    }

    public int getNumOfUniqueTimeSlots() {
        return numOfUniqueTimeSlots;
    }

    public int getAdditionalDays() {
        return additionalDays;
    }

    public int getNumOfSimulationRuns() {
        return numOfSimulationRuns;
    }

    public boolean isSingleAgentTypeUsed() {
        return isSingleAgentTypeUsed;
    }

    public AgentStrategyType getSelectedSingleAgentType() {
        return selectedSingleAgentType;
    }

    public boolean doesUtiliseSocialCapital() {
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

    public int getNumOfAgentsToEvolve() {
        return numOfAgentsToEvolve;
    }

    public int getSelfishPopulationCount() {
        return selfishPopulationCount;
    }

    public double[] getSatisfactionCurve() {
        return satisfactionCurve;
    }

    public ArrayList<Integer> getDemandCurveIndices() {
        return demandCurveIndices;
    }

    public double[][] getBucketedDemandCurves() {
        return bucketedDemandCurves;
    }

    public double[] getTotalDemandValues() {
        return totalDemandValues;
    }

    public int[] getBucketedAvailabilityCurve() {
        return bucketedAvailabilityCurve;
    }

    public int getTotalAvailableEnergy() {
        return totalAvailableEnergy;
    }

    /* Mutators */

    public Integer popFirstDemandCurveIndex() {
        return this.demandCurveIndices.removeFirst();
    }

    public void recreateDemandCurveIndices() {
        this.demandCurveIndices = this.createDemandCurveIndices();
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
    private AgentStrategyType inputToStrategyEnum(String input) {
        return input.equals("social") ? AgentStrategyType.SOCIAL : input.equals("selfish") ? AgentStrategyType.SELFISH : null;
    }

    private int ratioToSelfishPopulationCount(String input) {
        // Split the input string by colon
        String[] ratioParts = input.split(":");

        // Store the ratio of selfish:social agents provided in the input
        float selfishRatio = Float.parseFloat(ratioParts[0]);
        float socialRatio = Float.parseFloat(ratioParts[1]);

        // Calculate the amount of
        float fraction = this.populationCount / (selfishRatio + socialRatio);

        // Multiply the fraction of the population size by the ratio for each agent and round up the results
        return Math.round(fraction * selfishRatio);
    }

    // TODO: Cite Arena code
    private double[][] bucketSortDemandCurves() {
        double[][] bucketedDemandCurves = new double[this.demandCurves.length][this.numOfUniqueTimeSlots];

        for (int i = 0; i < this.demandCurves.length; i++) {
            double[] bucketedDemandCurve = new double[this.numOfUniqueTimeSlots];
            int bucket = 0;
            int bucketFill = 0;

            for (int j = 0; j < this.demandCurves[i].length; j++) {
                bucketedDemandCurve[bucket] = bucketedDemandCurve[bucket] + this.demandCurves[i][j];
                bucketFill++;

                if (bucketFill == 6) {
                    // Rounding to fix precision errors.
                    bucketedDemandCurve[bucket] = Math.round(bucketedDemandCurve[bucket] * 10.0) / 10.0;
                    bucketFill = 0;
                    bucket++;
                }
            }

            bucketedDemandCurves[i] = bucketedDemandCurve;
        }

        return bucketedDemandCurves;
    }

    // TODO: Cite Arena code
    private double[] calculateTotalDemandValues() throws NullPointerException {
        double[] totalDemandValues = new double[this.demandCurves.length];

        if (this.bucketedDemandCurves != null) {
            for (int i = 0; i < this.demandCurves.length; i++) {
                double totalDemand = 0;

                for (double demandValue : this.bucketedDemandCurves[i]) {
                    totalDemand = totalDemand + demandValue;
                }

                totalDemand = Math.round(totalDemand * 10.0) / 10.0;
                totalDemandValues[i] = totalDemand;
            }
        } else {
            System.err.println("The demand curves have not been bucketed yet.");
            throw new NullPointerException();
        }

        return totalDemandValues;
    }

    private ArrayList<Integer> createDemandCurveIndices() throws NullPointerException {
        ArrayList<Integer> unallocatedCurveIndices = new ArrayList<>();
        int curveIndex = 0;

        if (this.bucketedDemandCurves != null) {
            // TODO: Cite Arena code
            for (int i = 0; i < this.populationCount; i++) {
                unallocatedCurveIndices.add(curveIndex);
                curveIndex++;

                if (curveIndex >= this.bucketedDemandCurves.length) {
                    curveIndex = 0;
                }
            }
        } else {
            System.err.println("The demand curves have not been bucketed yet.");
            throw new NullPointerException();
        }

        Collections.shuffle(unallocatedCurveIndices, random);

        return unallocatedCurveIndices;
    }

    // TODO: Cite Arena code
    private int[] bucketSortAvailabilityCurve() {
        // The availability curve is bucketed before the simulations for efficiency, as they will all use the same bucketed values.
        int[] bucketedAvailabilityCurve = new int[this.numOfUniqueTimeSlots];

        int bucket = 0;
        int bucketFill = 0;
        int bucketValue = 0;

        for (int element : this.availabilityCurve) {
            bucketValue += element;
            bucketFill++;

            if (bucketFill == 2) {
                bucketedAvailabilityCurve[bucket] = bucketValue;
                bucket++;
                bucketValue = 0;
                bucketFill = 0;
            }
        }

        return bucketedAvailabilityCurve;
    }

    // TODO: Cite Arena code
    private int calculateTotalAvailableEnergy() {
        int totalAvailableEnergy = 0;

        for (int element : this.availabilityCurve) {
            totalAvailableEnergy += element;
        }

        return totalAvailableEnergy;
    }

    private int calculateNumberOfAgentsToEvolve(double agentsToEvolvePercentage) {
        return (int)Math.round(((double)this.populationCount / 100.0) * agentsToEvolvePercentage);
    }
}
