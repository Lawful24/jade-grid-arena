package com.napier.singletons;

import com.napier.types.AgentStrategyType;
import com.napier.types.ExchangeType;
import com.napier.Main;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Collections;

/**
 * A singleton class that imports and contains user defined settings from a configuration file
 * and uses them to calibrate the rules of a simulation set.
 *
 * @author László Tárkányi
 */
public class SimulationConfigurationSingleton {
    private static SimulationConfigurationSingleton instance;
    private final boolean debugMode;
    private ExchangeType exchangeType;
    private static final Random random = new Random();

    /* Configuration Properties */
    private final long startingSeed; // seed
    private final String resultsFolderPath; // results.folder
    private final String pythonExePath; // python.executable
    private final String pythonScriptsPath; // python.scripts
    private final int populationCount; // population.size
    private final int numOfSlotsPerAgent; // agent.time-slots
    private final int numOfUniqueTimeSlots; // simulation.uniqueTime-slots
    private final int numOfAdditionalDaysAfterTakeover; // simulation.additionalDays
    private final int numOfSimulationRuns; // simulation.runs
    private boolean doesUtiliseSingleAgentType; // agent.singleType
    private AgentStrategyType selectedSingleAgentType; // agent.selectedSingleType
    private boolean doesUtiliseSocialCapita; // agent.useSocialCapital
    private final double beta; // agent.beta
    private final int comparisonLevel; // simulation.comparisonLevel
    private final double[][] demandCurves; // demand.curves
    private final int[] availabilityCurve; // availability.curve
    private final double evolutionPercentage; // agents.evolvePercentage
    private final String agentTypeRatioInputString; // agent.typeRatio
    private final double[] satisfactionCurve; // agent.satisfactionCurve

    /* Calculated Values */
    private long currentSeed;
    private final double[][] bucketedDemandCurves;
    private final double[] totalDemandValues;
    private ArrayList<Integer> demandCurveIndices;
    private final int[] bucketedAvailabilityCurve;
    private final int totalAvailableEnergy;
    private final int numOfAgentsToEvolve;
    private int selfishPopulationCount;

    public static SimulationConfigurationSingleton getInstance() {
        if (instance == null) {
            instance = new SimulationConfigurationSingleton();
        }

        return instance;
    }

    private SimulationConfigurationSingleton() {
        this.debugMode = Main.isDebugMode();
        this.exchangeType = Main.getDefaultExchangeType();

        // Retrieve user parameters from the config file
        Properties properties = new Properties();
        loadPropertiesFromFile(properties, this.debugMode);

        // Read the configuration variables from the config properties and store them in the attributes
        this.startingSeed = Long.parseLong(properties.getProperty("seed"));
        this.resultsFolderPath = properties.getProperty("results.folder");
        this.pythonExePath = properties.getProperty("python.executable");
        this.pythonScriptsPath = properties.getProperty("python.scripts");
        this.populationCount = Integer.parseInt(properties.getProperty("population.size"));
        this.numOfSlotsPerAgent = Integer.parseInt(properties.getProperty("agent.time-slots"));
        this.numOfUniqueTimeSlots = Integer.parseInt(properties.getProperty("simulation.uniqueTime-slots"));
        this.numOfAdditionalDaysAfterTakeover = Integer.parseInt(properties.getProperty("simulation.additionalDays"));
        this.numOfSimulationRuns = Integer.parseInt(properties.getProperty("simulation.runs"));
        this.doesUtiliseSingleAgentType = Boolean.parseBoolean(properties.getProperty("agent.singleType"));
        this.selectedSingleAgentType = inputToStrategyEnum(properties.getProperty("agent.selectedSingleType"));
        this.doesUtiliseSocialCapita = Boolean.parseBoolean(properties.getProperty("agent.useSocialCapital"));
        this.beta = Integer.parseInt(properties.getProperty("agent.beta"));
        this.comparisonLevel = Integer.parseInt(properties.getProperty("simulation.comparisonLevel"));
        this.demandCurves = inputToDouble2DArray(properties.getProperty("demand.curves"));
        this.availabilityCurve = inputToIntArray(properties.getProperty("availability.curve"));
        this.evolutionPercentage = Double.parseDouble(properties.getProperty("agents.evolvePercentage"));
        this.agentTypeRatioInputString = properties.getProperty("agent.typeRatio");
        this.satisfactionCurve = inputToDoubleArray(properties.getProperty("agent.satisfactionCurve"));

        // Calculate values based on the configuration properties
        this.currentSeed = startingSeed;
        this.bucketedDemandCurves = this.bucketSortDemandCurves();
        this.totalDemandValues = this.calculateTotalDemandValues();
        this.demandCurveIndices = this.createDemandCurveIndices();
        this.bucketedAvailabilityCurve = this.bucketSortAvailabilityCurve();
        this.totalAvailableEnergy = this.calculateTotalAvailableEnergy();
        this.numOfAgentsToEvolve = this.calculateNumberOfAgentsToEvolve();
        this.selfishPopulationCount = ratioToSelfishPopulationCount();

        random.setSeed(this.currentSeed);
    }

    /* Accessors */

    public boolean isDebugMode() {
        return this.debugMode;
    }

    public ExchangeType getExchangeType() {
        return this.exchangeType;
    }

    public Random getRandom() {
        return random;
    }

    public long getStartingSeed() {
        return this.startingSeed;
    }

    public String getResultsFolderPath() {
        return this.resultsFolderPath;
    }

    public int getPopulationCount() {
        return this.populationCount;
    }

    public int getNumOfSlotsPerAgent() {
        return this.numOfSlotsPerAgent;
    }

    public int getNumOfUniqueTimeSlots() {
        return this.numOfUniqueTimeSlots;
    }

    public int getNumOfAdditionalDaysAfterTakeover() {
        return this.numOfAdditionalDaysAfterTakeover;
    }

    public int getNumOfSimulationRuns() {
        return this.numOfSimulationRuns;
    }

    public boolean doesUtiliseSingleAgentType() {
        return this.doesUtiliseSingleAgentType;
    }

    public AgentStrategyType getSelectedSingleAgentType() {
        return this.selectedSingleAgentType;
    }

    public boolean doesUtiliseSocialCapita() {
        return this.doesUtiliseSocialCapita;
    }

    public double getBeta() {
        return this.beta;
    }

    public int getComparisonLevel() {
        return this.comparisonLevel;
    }

    public double[][] getDemandCurves() {
        return this.demandCurves;
    }

    public int[] getAvailabilityCurve() {
        return this.availabilityCurve;
    }

    public int getNumOfAgentsToEvolve() {
        return this.numOfAgentsToEvolve;
    }

    public int getSelfishPopulationCount() {
        return this.selfishPopulationCount;
    }

    public double[] getSatisfactionCurve() {
        return this.satisfactionCurve;
    }

    public ArrayList<Integer> getDemandCurveIndices() {
        return this.demandCurveIndices;
    }

    public long getCurrentSeed() {
        return this.currentSeed;
    }

    public double[][] getBucketedDemandCurves() {
        return this.bucketedDemandCurves;
    }

    public double[] getTotalDemandValues() {
        return this.totalDemandValues;
    }

    public int[] getBucketedAvailabilityCurve() {
        return this.bucketedAvailabilityCurve;
    }

    public int getTotalAvailableEnergy() {
        return this.totalAvailableEnergy;
    }

    /* Mutators */

    public void incrementRandomSeed() {
        this.currentSeed++;
        random.setSeed(this.currentSeed);
    }

    /**
     * Restore the seed of the random object to the seed defined in the configuration file.
     */
    private void resetRandomSeed() {
        this.currentSeed = this.startingSeed;
        random.setSeed(this.currentSeed);
    }

    public void setExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
    }

    /**
     * At the start of a simulation set, this overwrites some of the user defined configuration settings.
     *
     * @param doesUtiliseSingleAgentType Whether the Household agents in the next simulation set should be allowed to choose between 1 or 2 strategy types.
     * @param selectedSingleAgentType The strategy type that the Household agents can use, if they can only use one. (If they can be either selfish or social, call getSelectedSingleAgentType().)
     * @param doesUtiliseSocialCapita Whether social capita should be used in the exchanges of the next simulation set.
     */
    public void modifyConfiguration(boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType, boolean doesUtiliseSocialCapita) {
        this.resetRandomSeed();
        this.setSingleAgentTypeUsed(doesUtiliseSingleAgentType, selectedSingleAgentType);
        this.setDoesUtiliseSocialCapita(doesUtiliseSocialCapita);
    }

    private void setSingleAgentTypeUsed(boolean doesUtiliseSingleAgentType, AgentStrategyType selectedSingleAgentType) {
        this.doesUtiliseSingleAgentType = doesUtiliseSingleAgentType;
        this.selectedSingleAgentType = selectedSingleAgentType;

        if (this.doesUtiliseSingleAgentType) {
            if (this.selectedSingleAgentType == AgentStrategyType.SOCIAL) {
                this.selfishPopulationCount = 0;
            } else {
                this.selfishPopulationCount = this.populationCount;
            }
        } else {
            this.selfishPopulationCount = this.ratioToSelfishPopulationCount();
        }
    }

    private void setDoesUtiliseSocialCapita(boolean doesUtiliseSocialCapital) {
        this.doesUtiliseSocialCapita = doesUtiliseSocialCapital;
    }

    /**
     * Gets and removes the first index of the demand curve array that determines the daily demand of a Household agent.
     *
     * @return (Integer) The element at the first position of the shuffled array containing indices.
     */
    public Integer popFirstDemandCurveIndex() {
        return this.demandCurveIndices.removeFirst();
    }

    /**
     * Resets the array that stores daily demand curves.
     */
    public void recreateDemandCurveIndices() {
        this.demandCurveIndices = this.createDemandCurveIndices();
    }

    /* Helpers */

    /**
     * Reads the configuration file and loads the user settings into a Properties object.
     *
     * @param properties The object that stores the settings from the configuration file.
     * @param isDebug Whether the application is running in Debug Mode or not. If Debug Mode is on, the debug.config.properties file is used.
     */
    private void loadPropertiesFromFile(Properties properties, boolean isDebug) {
        String configFilename;

        // Choose the configuration file based on execution mode
        if (isDebug) {
            configFilename = "debug.config.properties";
        } else {
            configFilename = "config.properties";
        }

        // Load the properties from the configuration file
        try (InputStream input = new FileInputStream(configFilename)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Converts a String into a double array.
     * The String has to be in the following format: 1.0,1.0,0.0,1.0
     *
     * @param input The text containing the values of the array.
     * @return (double[]) The array form of the input text.
     */
    private double[] inputToDoubleArray(String input) {
        return Arrays.stream(input.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    // TODO: Cite Arena code
    /**
     * Converts a String into a two-dimensional double array.
     * The String has to be in the following format: 1.0,1.0,0.0,1.0||0.0,0.0,1.0,0.0
     *
     * @param input The text containing the values of the array.
     * @return (double[][]) The array form of the input text.
     */
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

    /**
     * Converts a String into a double array.
     * The String has to be in the following format: 1,1,0,1
     *
     * @param input The text containing the values of the array.
     * @return (int[]) The array form of the input text.
     */
    private int[] inputToIntArray(String input) {
        return Arrays.stream(input.split(",")).mapToInt(Integer::parseInt).toArray();
    }

    // TODO: Flag as nullable
    /**
     * Converts a String to an agent strategy type enum.
     * The String has to be in the following format: social
     *
     * @param input The String containing the strategy type.
     * @return (AgentStrategyType) The enum form of the input text.
     */
    private AgentStrategyType inputToStrategyEnum(String input) {
        return input.equals("social") ? AgentStrategyType.SOCIAL : input.equals("selfish") ? AgentStrategyType.SELFISH : null;
    }

    /**
     * Converts a String ratio into the number of selfish Household agents in the simulation set.
     * The String has to be in the following format: 2:1
     * Float values are supported: 2.01:1.39472
     *
     * @return (int) The number of selfish Household agents that form the selfish population.
     */
    private int ratioToSelfishPopulationCount() {
        // Split the input string by colon
        String[] ratioParts = this.agentTypeRatioInputString.split(":");

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

    /**
     * Generates indices in a list that point to values in the generated daily demand curves array.
     * This allows Household agents to have a different demand for timeslots at the start of each day.
     *
     * @return (ArrayList of Integers) The shuffled indices that point to demand curves.
     * @throws NullPointerException If the demand curves have not been bucket sorted before this is called.
     */
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

    private int calculateNumberOfAgentsToEvolve() {
        return (int)Math.round(((double)this.populationCount / 100.0) * this.evolutionPercentage);
    }
}