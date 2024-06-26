package com.napier.arena.agents;

import com.napier.arena.AgentHelper;
import com.napier.arena.concepts.AgentContact;
import com.napier.arena.concepts.dataholders.EndOfDayAdvertisingBoardDataHolder;
import com.napier.arena.concepts.dataholders.PopulationEndOfDayDataHolder;
import com.napier.arena.singletons.BlockchainSingleton;
import com.napier.arena.singletons.SimulationConfigurationSingleton;
import com.napier.arena.singletons.DataOutputSingleton;
import com.napier.arena.singletons.TickerTrackerSingleton;
import com.napier.arena.types.AgentStrategyType;
import com.napier.arena.types.ExchangeType;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * An agent that keeps track of the time passed in the application and synchronises the communication between other agents.
 * Only one of them can exist in this application.
 *
 * @author László Tárkányi
 */
public class TickerAgent extends Agent {
    // Simulation tracker attributes
    private int currentSimulationSet;
    private int currentSimulationRun;
    private int currentDay;
    private int currentDayAfterTakeover;
    private boolean takeover;

    // Takeover data attributes
    private ArrayList<PopulationEndOfDayDataHolder> socialPopulationEndOfDayDataHolders;
    private ArrayList<PopulationEndOfDayDataHolder> selfishPopulationEndOfDayDataHolders;
    private ArrayList<PopulationEndOfDayDataHolder> socialFinalDayDataHolders;
    private ArrayList<PopulationEndOfDayDataHolder> selfishFinalDayDataHolders;
    private int numOfSocialTakeoverRuns;
    private int numOfSelfishTakeoverRuns;

    // Agent contact attributes
    private ArrayList<AID> allAgentIdentifiers;
    private ArrayList<AgentContact> householdAgentContacts;
    private AID advertisingAgent;

    // Singletons
    private SimulationConfigurationSingleton config;
    private TickerTrackerSingleton timeTracker;
    private DataOutputSingleton outputInstance;
    private BlockchainSingleton blockchainReference;

    @Override
    protected void setup() {
        this.initialAgentSetup();

        AgentHelper.registerAgent(this, "Ticker");

        // Wait until the other agents are created and registered
        doWait(1500);

        // Add the initial behaviours
        addBehaviour(new FindHouseholdsBehaviour(this));
        addBehaviour(new FindAdvertisingBoardBehaviour(this));
        addBehaviour(new DailySyncBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);

        outputInstance.closeAllDataWriters();

        System.exit(0);
    }

    /**
     * Seeks out all the Household type agents and save them for contacting them in the future.
     * A reusable behaviour of TickerAgent.
     */
    public class FindHouseholdsBehaviour extends OneShotBehaviour {
        public FindHouseholdsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Populate the contact collection
            householdAgentContacts = AgentHelper.saveAgentContacts(myAgent, "Household");
        }
    }

    /**
     * Seeks out the only Advertising Board type agent and saves it for contacting it in the future.
     * A reusable behaviour of TickerAgent.
     */
    public class FindAdvertisingBoardBehaviour extends OneShotBehaviour {
        public FindAdvertisingBoardBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst().getAgentIdentifier();
        }
    }

    /**
     * The daily cycle of notifying all other agents about the start of a day or a simulation run, and listening for when those agents are done with their day.
     * A reusable behaviour of TickerAgent.
     */
    public class DailySyncBehaviour extends Behaviour {
        private int step = 1;
        private EndOfDayAdvertisingBoardDataHolder endOfDayData = null;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            /*
            The source of the step logic:
            Dr Simon Powers: SET10111 Multi-Agent Systems - Practical textbook; Edinburgh Napier University
            */

            switch (step) {
                // Step 1: Let all other agents know that the new day (or a new simulation run) has started
                case 1:
                    // Reshuffle the daily demand curve allocation
                    config.recreateDemandCurveIndices();

                    // Collect all receivers
                    if (allAgentIdentifiers.size() <= config.getPopulationCount()) {
                        allAgentIdentifiers.clear();

                        for (AgentContact householdAgentContact : householdAgentContacts) {
                            allAgentIdentifiers.add(householdAgentContact.getAgentIdentifier());
                        }

                        allAgentIdentifiers.add(advertisingAgent);
                    }

                    // Reset the values used in each simulation run on the first day of the run
                    if (currentDay == 1) {
                        runReset();

                        // Set up the simulation set on the first day of the
                        if (currentSimulationRun == 1) {
                            setupSimulationSet();

                            // Reset information singletons to their default state
                            timeTracker.resetTracking();
                        }

                        AgentHelper.printAgentLog(
                                myAgent.getLocalName(),
                                "\nStarted Run " + currentSimulationRun + "/" + config.getNumOfSimulationRuns()
                                        + " with " + config.getPopulationCount() + " agents."
                                        + " Exchange Type: " + config.getExchangeType());

                        // Broadcast the start of the new simulation run to all other agents
                        AgentHelper.sendMessage(
                                myAgent,
                                allAgentIdentifiers,
                                "New Run",
                                ACLMessage.INFORM
                        );

                        // Track the start of a new run and reset the day tracking
                        timeTracker.incrementCurrentSimulationRun();
                        timeTracker.resetDayTracking();
                    } else {
                        // Broadcast the start of the new day to all other agents
                        AgentHelper.sendMessage(
                                myAgent,
                                allAgentIdentifiers,
                                "New Day",
                                ACLMessage.INFORM
                        );
                    }

                    // Progress the current day tracking
                    timeTracker.incrementCurrentDay();

                    // Progress the state of the behaviour
                    step++;

                    break;
                case 2:
                    // Step 2: Be notified when the Advertising agent announces that the day is over

                    // Receive a done message from the Advertising agent when a day is over
                    // The Advertising agent will send this message when the exchange has timed out
                    ACLMessage advertisingDayOverMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, "Done", ACLMessage.INFORM);

                    if (advertisingDayOverMessage != null) {
                        // Make sure the incoming object is readable
                        Serializable receivedObject = AgentHelper.readReceivedContentObject(advertisingDayOverMessage, myAgent.getLocalName(), EndOfDayAdvertisingBoardDataHolder.class);

                        // Make sure the incoming object is of the expected type
                        if (receivedObject instanceof EndOfDayAdvertisingBoardDataHolder dataHolder) {
                            // Store the data provided by the Advertising agent
                            endOfDayData = dataHolder;
                            // Overwrite the existing list of contacts with the updated list
                            householdAgentContacts = dataHolder.contacts();
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Agent contact list was not updated: the received object has an incorrect type or is null.");
                        }

                        // Progress the state of the behaviour
                        step++;
                    } else {
                        block();
                    }
                default:
                    break;
            }
        }

        @Override
        public boolean done() {
            return step == 3;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "End of day " + currentDay);
            }

            // Make sure that the incoming data from the Advertising agent is not null
            if (endOfDayData != null) {
                /*
                The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
                See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java
                */

                // Check if a takeover has happened in the current run
                // This will trigger the count of the additional days defined in the configuration file
                if (((endOfDayData.numOfSelfishAgents() == 0 || endOfDayData.numOfSocialAgents() == 0) || config.getNumOfAgentsToEvolve() == 0) && !takeover) {
                    takeover = true;
                    extractTakeoverData(endOfDayData, false);

                    if (config.isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "takeover! social agents: " + endOfDayData.numOfSocialAgents() + " selfish agents: " + endOfDayData.numOfSelfishAgents());
                    }
                }

                // If the current additional day count has reached the defined limit, the run is finished
                if (currentDayAfterTakeover == config.getNumOfAdditionalDaysAfterTakeover()) {
                    extractTakeoverData(endOfDayData, true);

                    AgentHelper.printAgentLog(
                            myAgent.getLocalName(),
                            "Days: "
                                    + currentDay
                                    + ", Takeover: "
                                    + householdAgentContacts.getFirst().getType()
                                    + ", Average satisfaction: "
                                    + (endOfDayData.averageSocialSatisfaction() + endOfDayData.averageSelfishSatisfaction())
                                    + "\n"
                    );

                    // Check if the current run is the last run in the current simulation set, based on the configuration file
                    if (currentSimulationRun == config.getNumOfSimulationRuns()) {
                        writeSimulationData();

                        // Determine if the program should continue with another simulation set or if it should exit
                        if (shouldShutEnvironmentDown()) {
                            // Broadcast the Terminate message to all other agents
                            AgentHelper.sendMessage(
                                    myAgent,
                                    allAgentIdentifiers,
                                    "Terminate",
                                    ACLMessage.INFORM
                            );

                            // Terminate the ticker agent itself
                            myAgent.doDelete();
                        } else {
                            // Progress the program by setting up and executing the next simulation set
                            simulationReset();
                            currentSimulationSet++;

                            // Flush the transactions from the blockchain's ledger
                            blockchainReference.resetBlockchain();

                            // Update the Household agent contacts
                            myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));

                            // Recreate this behaviour by adding it to the agent's behaviour queue
                            myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                        }
                    } else {
                        // Progress the simulation set by setting up the next run
                        currentSimulationRun++;
                        runReset();

                        config.incrementRandomSeed();

                        // Flush the transactions from the blockchain's ledger
                        blockchainReference.resetBlockchain();

                        // Update the Household agent contacts
                        myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));

                        // Recreate this behaviour by adding it to the agent's behaviour queue
                        myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                    }
                } else {
                    // Progress the current run by starting a new day
                    currentDay++;

                    // Check if a Household agent type takeover has happened yet
                    if (takeover) {
                        currentDayAfterTakeover++;
                    }

                    // Update the Household agent contacts
                    myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));

                    // Recreate this behaviour by adding it to the agent's behaviour queue
                    myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                }
            } else {
                AgentHelper.printAgentError(myAgent.getLocalName(), "Failed to process end of day Advertising agent data: incoming object is null.");
            }

            return 0;
        }
    }

    /**
     * Sets the initial state of the agent.
     */
    private void initialAgentSetup() {
        this.config = SimulationConfigurationSingleton.getInstance();
        this.timeTracker = TickerTrackerSingleton.getInstance();
        this.outputInstance = DataOutputSingleton.getInstance();
        this.blockchainReference = BlockchainSingleton.getInstance();

        this.currentSimulationSet = 1;
        this.allAgentIdentifiers = new ArrayList<>();

        this.simulationReset();
        this.runReset();
    }

    /**
     * Marks the start of a simulation set and modifies the configuration according to the comparison level.
     */
    private void setupSimulationSet() {
        this.simulationReset();

        // Modify the simulation configuration based on the defined comparison level and current simulation set
        switch (config.getComparisonLevel()) {
            case 1:
                switch (this.currentSimulationSet) {
                    case 1:
                        config.modifyConfiguration(false, config.getSelectedSingleAgentType(), true, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + "Starting new simulation set: with social capita"
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.modifyConfiguration(false, config.getSelectedSingleAgentType(), false, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + "Starting new simulation set: without social capita"
                                        + "----------------\n"
                        );

                        break;
                }

                break;
            case 2:
                switch (this.currentSimulationSet) {
                    case 1:
                        config.modifyConfiguration(true, AgentStrategyType.SELFISH, false, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, only selfish agents "
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.modifyConfiguration(true, AgentStrategyType.SOCIAL, false, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, only social agents "
                                        + "----------------\n"
                        );

                        break;
                    case 3:
                        config.modifyConfiguration(true, AgentStrategyType.SOCIAL, true, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: with social capita, only social agents "
                                        + "----------------\n"
                        );

                        break;
                    case 4:
                        config.modifyConfiguration(false, config.getSelectedSingleAgentType(), true, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, no agent type restrictions "
                                        + "----------------\n"
                        );

                        break;
                    case 5:
                        config.modifyConfiguration(false, config.getSelectedSingleAgentType(), true, ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: with social capita, no agent type restrictions "
                                        + "----------------\n"
                        );

                        break;
                }

                break;
            case 3:
                switch (this.currentSimulationSet) {
                    case 1:
                        config.modifyConfiguration(config.doesUtiliseSingleAgentType(), config.getSelectedSingleAgentType(), config.doesUtiliseSocialCapita(), ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: user determined settings, Message Passing exchange type "
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.modifyConfiguration(config.doesUtiliseSingleAgentType(), config.getSelectedSingleAgentType(), config.doesUtiliseSocialCapita(), ExchangeType.SmartContract);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: user determined settings, Smart Contract exchange type "
                                        + "----------------\n"
                        );

                        break;
                }

                break;
            default:
                AgentHelper.printAgentLog(
                        getLocalName(),
                        "----------------"
                                + " Starting new simulation set: user determined settings only "
                                + "----------------\n"
                );

                break;
        }

        // Create the output folder and files to store the resulting data in
        outputInstance.prepareSimulationDataOutput(
                config.doesUtiliseSocialCapita(),
                config.doesUtiliseSingleAgentType(),
                config.getSelectedSingleAgentType()
        );
    }

    /**
     * Sets the state of the agent to the same as before the first simulation set started.
     */
    private void simulationReset() {
        this.runReset();

        this.currentSimulationRun = 1;
        this.socialPopulationEndOfDayDataHolders = new ArrayList<>();
        this.selfishPopulationEndOfDayDataHolders = new ArrayList<>();
        this.socialFinalDayDataHolders = new ArrayList<>();
        this.selfishFinalDayDataHolders = new ArrayList<>();
        this.numOfSocialTakeoverRuns = 0;
        this.numOfSelfishTakeoverRuns = 0;
    }

    /**
     * Sets the state of the agent to the same as before the first run of the current simulation run started.
     */
    private void runReset() {
        this.currentDay = 1;
        this.currentDayAfterTakeover = 0;
        this.takeover = false;
    }

    /**
     * Filters the daily data collected by the Advertising agent to takeover data.
     *
     * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java">ResourceExchangeArena</a>
     *
     * @param endOfDayData The holder of the data that the Advertising agent collected during a day.
     * @param isFinalDayOfRun Whether the current day is the final day of the simulation run or not.
     */
    private void extractTakeoverData(EndOfDayAdvertisingBoardDataHolder endOfDayData, boolean isFinalDayOfRun) {
        if (!isFinalDayOfRun) {
            if (endOfDayData.numOfSelfishAgents() == 0.0) {
                this.socialPopulationEndOfDayDataHolders.add(new PopulationEndOfDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSocialSatisfaction(),
                        endOfDayData.averageSocialSatisfactionStandardDeviation()
                ));

                this.numOfSocialTakeoverRuns++;
            } else {
                this.selfishPopulationEndOfDayDataHolders.add(new PopulationEndOfDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSelfishSatisfaction(),
                        endOfDayData.averageSelfishSatisfactionStandardDeviation()
                ));

                this.numOfSelfishTakeoverRuns++;
            }
        } else {
            if (endOfDayData.numOfSelfishAgents() == 0.0) {
                this.socialFinalDayDataHolders.add(new PopulationEndOfDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSocialSatisfaction(),
                        endOfDayData.averageSocialSatisfactionStandardDeviation()
                ));
            } else {
                this.selfishFinalDayDataHolders.add(new PopulationEndOfDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSelfishSatisfaction(),
                        endOfDayData.averageSelfishSatisfactionStandardDeviation()
                ));
            }
        }
    }

    /**
     * Outputs overall simulation data for each strategy type takeover.
     *
     * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/ArenaEnvironment.java">ResourceExchangeArena</a>
     */
    private void writeSimulationData() {
        if (this.numOfSocialTakeoverRuns > 0 || this.numOfSelfishTakeoverRuns > 0) {
            int middleSocialRun = 0;
            int middleSelfishRun = 0;

            if (this.numOfSocialTakeoverRuns > 0) {
                middleSocialRun = processTakeoverDataByType(AgentStrategyType.SOCIAL);
            }

            if (this.numOfSelfishTakeoverRuns > 0) {
                middleSelfishRun = processTakeoverDataByType(AgentStrategyType.SELFISH);
            }

            try {
                outputInstance.initiateSimulationVisualiser(middleSocialRun, middleSelfishRun);
            } catch (IOException e) {
                AgentHelper.printAgentError(getLocalName(), "A problem occurred while trying to run the data visualisation scripts.");
                e.printStackTrace();
            }
        } else {
            AgentHelper.printAgentError(getLocalName(), "No takeovers have occurred or they have not been recorded.");
        }

        outputInstance.flushAllDataWriters();
    }

    /**
     * Determines whether the application is at a state where it should shut down.
     *
     * @return (boolean) True if all the simulation sets have finished based on the comparison level defined in the configuration file.
     */
    private boolean shouldShutEnvironmentDown() {
        boolean shutdown;

        switch (config.getComparisonLevel()) {
            case 1, 3 -> shutdown = this.currentSimulationSet == 2;
            case 2 -> shutdown = this.currentSimulationSet == 5;
            default -> shutdown = true;
        }

        return shutdown;
    }

    /**
     * Processes and sends the filtered takeover data to the data writer for a selected agent strategy type.
     *
     * @param agentStrategyType The type of takeovers that should be processed.
     * @return (int) The number of the median speed simulation run of all takeover runs from the given type.
     */
    private int processTakeoverDataByType(AgentStrategyType agentStrategyType) {
        double takeoverDaysSum = 0;
        double averageSatisfactionsOnTakeoverDaySum = 0;
        double averageSatisfactionStandardDeviationsOnTakeoverDaySum = 0;
        double averageSatisfactionsOnFinalDaySum = 0;
        double averageSatisfactionStandardDeviationsOnFinalDaySum = 0;
        ArrayList<PopulationEndOfDayDataHolder> populationEndOfDayDataHolders;
        ArrayList<PopulationEndOfDayDataHolder> finalDayDataHolders;
        int numOfTypeTakeoverRuns;

        // Determine the type of data to use
        if (agentStrategyType == AgentStrategyType.SOCIAL) {
            populationEndOfDayDataHolders = this.socialPopulationEndOfDayDataHolders;
            finalDayDataHolders = this.socialFinalDayDataHolders;
            numOfTypeTakeoverRuns = this.numOfSocialTakeoverRuns;
        } else {
            populationEndOfDayDataHolders = this.selfishPopulationEndOfDayDataHolders;
            finalDayDataHolders = this.selfishFinalDayDataHolders;
            numOfTypeTakeoverRuns = this.numOfSelfishTakeoverRuns;
        }

        /*
        The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
        See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/ArenaEnvironment.java
        */
        Comparator<PopulationEndOfDayDataHolder> takeoverDataHolderComparator = Comparator.comparing(PopulationEndOfDayDataHolder::day);

        populationEndOfDayDataHolders.sort(takeoverDataHolderComparator);
        finalDayDataHolders.sort(takeoverDataHolderComparator);

        for(PopulationEndOfDayDataHolder run: populationEndOfDayDataHolders) {
            takeoverDaysSum += run.day();
            averageSatisfactionsOnTakeoverDaySum += run.averageSatisfaction();
            averageSatisfactionStandardDeviationsOnTakeoverDaySum += run.averageSatisfactionStandardDeviation();
        }

        for(PopulationEndOfDayDataHolder run: finalDayDataHolders) {
            averageSatisfactionsOnFinalDaySum += run.averageSatisfaction();
            averageSatisfactionStandardDeviationsOnFinalDaySum += run.averageSatisfactionStandardDeviation();
        }

        PopulationEndOfDayDataHolder middleTakeover = populationEndOfDayDataHolders.get((int) Math.floor(numOfTypeTakeoverRuns / 2.0f));
        PopulationEndOfDayDataHolder slowestTakeover = populationEndOfDayDataHolders.get(numOfTypeTakeoverRuns - 1);
        PopulationEndOfDayDataHolder fastestTakeover = populationEndOfDayDataHolders.getFirst();

        DataOutputSingleton.getInstance().appendSimulationDataByTakeoverType(
                agentStrategyType,
                numOfTypeTakeoverRuns,
                fastestTakeover.simulationRun(),
                slowestTakeover.simulationRun(),
                middleTakeover.simulationRun(),
                finalDayDataHolders.size(),
                takeoverDaysSum,
                averageSatisfactionsOnTakeoverDaySum,
                averageSatisfactionsOnFinalDaySum,
                averageSatisfactionStandardDeviationsOnTakeoverDaySum,
                averageSatisfactionStandardDeviationsOnFinalDaySum
        );

        return middleTakeover.simulationRun();
    }
}