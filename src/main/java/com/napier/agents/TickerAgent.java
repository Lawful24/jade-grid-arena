package com.napier.agents;

import com.napier.AgentHelper;
import com.napier.concepts.AgentContact;
import com.napier.concepts.dataholders.EndOfDayAdvertisingBoardDataHolder;
import com.napier.concepts.dataholders.TakeoverDayDataHolder;
import com.napier.singletons.BlockchainSingleton;
import com.napier.singletons.SimulationConfigurationSingleton;
import com.napier.singletons.DataOutputSingleton;
import com.napier.singletons.TickerTrackerSingleton;
import com.napier.types.AgentStrategyType;
import com.napier.types.ExchangeType;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;

public class TickerAgent extends Agent {
    // Simulation tracker attributes
    private int currentSimulationSet;
    private int currentSimulationRun;
    private int currentDay;
    private int currentDayAfterTakeover;
    private boolean takeover;

    // Takeover data attributes
    private ArrayList<TakeoverDayDataHolder> socialTakeoverDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> selfishTakeoverDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> socialFinalDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> selfishFinalDayDataHolders;
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

        System.exit(0);
    }

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

    public class FindAdvertisingBoardBehaviour extends OneShotBehaviour {
        public FindAdvertisingBoardBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst().getAgentIdentifier();
        }
    }

    public class DailySyncBehaviour extends Behaviour {
        private int step = 1;
        private EndOfDayAdvertisingBoardDataHolder endOfDayData = null;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // TODO: Cite JADE workbook for the step logic
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
                            config.resetRandomSeed();
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

                    // Progress the agent state
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

                        // Progress the agent state
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
                // TODO: Cite Arena code
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

                            outputInstance.closeAllDataWriters();

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

    private void setupSimulationSet() {
        this.simulationReset();

        // Modify the simulation configuration based on the defined comparison level and current simulation set
        switch (config.getComparisonLevel()) {
            case 1:
                switch (this.currentSimulationSet) {
                    case 1:
                        config.setDoesUtiliseSocialCapita(true);
                        config.setSingleAgentTypeUsed(false);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + "Starting new simulation set: with social capita"
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.setDoesUtiliseSocialCapita(false);
                        config.setSingleAgentTypeUsed(false);

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
                        config.setDoesUtiliseSocialCapita(false);
                        config.setSingleAgentTypeUsed(true, AgentStrategyType.SELFISH);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, only selfish agents "
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.setDoesUtiliseSocialCapita(false);
                        config.setSingleAgentTypeUsed(true, AgentStrategyType.SOCIAL);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, only social agents "
                                        + "----------------\n"
                        );

                        break;
                    case 3:
                        config.setDoesUtiliseSocialCapita(true);
                        config.setSingleAgentTypeUsed(true, AgentStrategyType.SOCIAL);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: with social capita, only social agents "
                                        + "----------------\n"
                        );

                        break;
                    case 4:
                        config.setDoesUtiliseSocialCapita(false);
                        config.setSingleAgentTypeUsed(false);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: without social capita, no agent type restrictions "
                                        + "----------------\n"
                        );

                        break;
                    case 5:
                        config.setDoesUtiliseSocialCapita(true);
                        config.setSingleAgentTypeUsed(false);

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
                        config.setExchangeType(ExchangeType.MessagePassing);

                        AgentHelper.printAgentLog(
                                getLocalName(),
                                "----------------"
                                        + " Starting new simulation set: user determined settings, Message Passing exchange type "
                                        + "----------------\n"
                        );

                        break;
                    case 2:
                        config.setExchangeType(ExchangeType.SmartContract);

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

    private void simulationReset() {
        this.runReset();

        this.currentSimulationRun = 1;
        this.socialTakeoverDayDataHolders = new ArrayList<>();
        this.selfishTakeoverDayDataHolders = new ArrayList<>();
        this.socialFinalDayDataHolders = new ArrayList<>();
        this.selfishFinalDayDataHolders = new ArrayList<>();
        this.numOfSocialTakeoverRuns = 0;
        this.numOfSelfishTakeoverRuns = 0;
    }

    private void runReset() {
        this.currentDay = 1;
        this.currentDayAfterTakeover = 0;
        this.takeover = false;
    }

    private void extractTakeoverData(EndOfDayAdvertisingBoardDataHolder endOfDayData, boolean isFinalDayOfRun) {
        // TODO: Cite Arena code
        if (isFinalDayOfRun) {
            if (endOfDayData.numOfSelfishAgents() == 0.0) {
                this.socialTakeoverDayDataHolders.add(new TakeoverDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSocialSatisfaction(),
                        endOfDayData.averageSocialSatisfactionStandardDeviation()
                ));

                this.numOfSocialTakeoverRuns++;
            } else {
                this.selfishTakeoverDayDataHolders.add(new TakeoverDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSelfishSatisfaction(),
                        endOfDayData.averageSelfishSatisfactionStandardDeviation()
                ));

                this.numOfSelfishTakeoverRuns++;
            }
        } else {
            if (endOfDayData.numOfSelfishAgents() == 0.0) {
                this.socialFinalDayDataHolders.add(new TakeoverDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSocialSatisfaction(),
                        endOfDayData.averageSocialSatisfactionStandardDeviation()
                ));
            } else {
                this.selfishFinalDayDataHolders.add(new TakeoverDayDataHolder(
                        this.currentSimulationRun,
                        this.currentDay,
                        endOfDayData.averageSelfishSatisfaction(),
                        endOfDayData.averageSelfishSatisfactionStandardDeviation()
                ));
            }
        }
    }

    private void writeSimulationData() {
        if (this.numOfSocialTakeoverRuns > 0 || this.numOfSelfishTakeoverRuns > 0) {
            // TODO: Cite Arena code
            int middleSocialRun = 0;
            int middleSelfishRun = 0;

            if (this.numOfSocialTakeoverRuns > 0) {
                middleSocialRun = processTakeoverDataByType(AgentStrategyType.SOCIAL);
            }

            if (this.numOfSelfishTakeoverRuns > 0) {
                middleSelfishRun = processTakeoverDataByType(AgentStrategyType.SELFISH);
            }
        } else {
            // TODO
        }

        outputInstance.flushAllDataWriters();
    }

    private boolean shouldShutEnvironmentDown() {
        boolean shutdown;

        switch (config.getComparisonLevel()) {
            case 1, 3 -> shutdown = this.currentSimulationSet == 2;
            case 2 -> shutdown = this.currentSimulationSet == 5;
            default -> shutdown = true;
        }

        return shutdown;
    }

    private int processTakeoverDataByType(AgentStrategyType agentStrategyType) {
        double takeoverDaysSum = 0;
        double averageSatisfactionsSum = 0;
        double averageSatisfactionStandardDeviationsSum = 0;
        ArrayList<TakeoverDayDataHolder> takeoverDayDataHolders;
        ArrayList<TakeoverDayDataHolder> finalDayDataHolders;
        int numOfTypeTakeoverRuns;

        // Determine the type of data to use
        if (agentStrategyType == AgentStrategyType.SOCIAL) {
            takeoverDayDataHolders = this.socialTakeoverDayDataHolders;
            finalDayDataHolders = this.socialFinalDayDataHolders;
            numOfTypeTakeoverRuns = this.numOfSocialTakeoverRuns;
        } else {
            takeoverDayDataHolders = this.selfishTakeoverDayDataHolders;
            finalDayDataHolders = this.selfishFinalDayDataHolders;
            numOfTypeTakeoverRuns = this.numOfSelfishTakeoverRuns;
        }

        // TODO: Cite Arena code
        Comparator<TakeoverDayDataHolder> takeoverDataHolderComparator = Comparator.comparing(TakeoverDayDataHolder::takeoverDay);

        takeoverDayDataHolders.sort(takeoverDataHolderComparator);
        finalDayDataHolders.sort(takeoverDataHolderComparator);

        for(TakeoverDayDataHolder run: takeoverDayDataHolders) {
            takeoverDaysSum += run.takeoverDay();
            averageSatisfactionsSum += run.averageSatisfaction();
            averageSatisfactionStandardDeviationsSum += run.averageSatisfactionStandardDeviation();
        }

        takeoverDaysSum = 0;
        averageSatisfactionsSum = 0;
        averageSatisfactionStandardDeviationsSum = 0;

        for(TakeoverDayDataHolder run: finalDayDataHolders) {
            takeoverDaysSum += run.takeoverDay();
            averageSatisfactionsSum += run.averageSatisfaction();
            averageSatisfactionStandardDeviationsSum += run.averageSatisfactionStandardDeviation();
        }

        TakeoverDayDataHolder middleTakeover = takeoverDayDataHolders.get((int) Math.floor(numOfTypeTakeoverRuns / 2.0f));
        TakeoverDayDataHolder slowestTakeover = takeoverDayDataHolders.get(numOfTypeTakeoverRuns - 1);
        TakeoverDayDataHolder fastestTakeover = takeoverDayDataHolders.getFirst();

        DataOutputSingleton.getInstance().appendSimulationDataForSocialRuns(
                agentStrategyType,
                numOfTypeTakeoverRuns,
                fastestTakeover.simulationRun(),
                slowestTakeover.simulationRun(),
                middleTakeover.simulationRun(),
                finalDayDataHolders.size(),
                takeoverDaysSum,
                averageSatisfactionsSum,
                averageSatisfactionStandardDeviationsSum
        );

        return middleTakeover.simulationRun();
    }
}
