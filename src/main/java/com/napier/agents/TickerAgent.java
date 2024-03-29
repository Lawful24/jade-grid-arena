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
    private int currentSimulationSet;
    private int currentSimulationRun;
    private int currentDay;
    private int currentDayAfterTakeover;
    private boolean takeover;
    private ArrayList<TakeoverDayDataHolder> socialTakeoverDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> selfishTakeoverDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> socialFinalDayDataHolders;
    private ArrayList<TakeoverDayDataHolder> selfishFinalDayDataHolders;
    private int numOfSocialTakeoverRuns;
    private int numOfSelfishTakeoverRuns;

    // Agent contact attributes
    private ArrayList<AID> allAgents;
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

        this.config = SimulationConfigurationSingleton.getInstance();
        this.timeTracker = TickerTrackerSingleton.getInstance();
        this.outputInstance = DataOutputSingleton.getInstance();
        this.blockchainReference = BlockchainSingleton.getInstance();

        doWait(1500);
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
            // Populate the contact collections
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
        private int step = 0;
        EndOfDayAdvertisingBoardDataHolder endOfDayData = null;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // TODO: Cite JADE workbook for the step logic
            switch (step) {
                case 0:
                    // Reshuffle the daily demand curve allocation
                    config.recreateDemandCurveIndices();

                    // Collect all receivers
                    if (allAgents.size() <= config.getPopulationCount()) {
                        allAgents.clear();

                        for (AgentContact householdAgentContact : householdAgentContacts) {
                            allAgents.add(householdAgentContact.getAgentIdentifier());
                        }

                        allAgents.add(advertisingAgent);
                    }

                    // Reset the values used in each simulation run on the first day of the run
                    if (currentDay == 1) {
                        runReset();

                        // Set up the simulation set on the first day of the
                        if (currentSimulationRun == 1) {
                            setupSimulationSet();

                            config.resetRandomSeed();
                            timeTracker.resetTracking();
                        }

                        AgentHelper.printAgentLog(
                                myAgent.getLocalName(),
                                "\nStarted Run " + currentSimulationRun + "/" + config.getNumOfSimulationRuns()
                                        + " with " + config.getPopulationCount() + " agents."
                                        + " Exchange Type: " + config.getExchangeType());

                        AgentHelper.sendMessage(
                                myAgent,
                                allAgents,
                                "New Run",
                                ACLMessage.INFORM
                        );

                        timeTracker.incrementCurrentSimulationRun();
                        timeTracker.resetDayTracking();
                    } else {
                        // Broadcast the start of the new day to other agents
                        AgentHelper.sendMessage(
                                myAgent,
                                allAgents,
                                "New Day",
                                ACLMessage.INFORM
                        );
                    }

                    timeTracker.incrementCurrentDay();

                    // Progress the agent state
                    step++;

                    break;
                case 1:
                    ACLMessage advertisingDayOverMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, "Done", ACLMessage.INFORM);

                    if (advertisingDayOverMessage != null) {
                        // Make sure the incoming object is readable
                        Serializable receivedObject = AgentHelper.readReceivedContentObject(advertisingDayOverMessage, myAgent.getLocalName(), EndOfDayAdvertisingBoardDataHolder.class);

                        // Make sure the incoming object is of the expected type
                        if (receivedObject instanceof EndOfDayAdvertisingBoardDataHolder dataHolder) {
                            endOfDayData = dataHolder;

                            // Overwrite the existing list of contacts with the updated list
                            householdAgentContacts = dataHolder.contacts();
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Agent contact list was not updated: the received object has an incorrect type or is null.");
                        }

                        step++;
                    } else {
                        block();
                    }
            }
        }

        @Override
        public boolean done() {
            return step == 2;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "End of day " + currentDay);
            }

            if (endOfDayData != null) {
                // TODO: Cite Arena code
                if (((endOfDayData.numOfSelfishAgents() == 0 || endOfDayData.numOfSocialAgents() == 0) || config.getNumOfAgentsToEvolve() == 0) && !takeover) {
                    takeover = true;

                    if (config.isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "takeover! social agents: " + endOfDayData.numOfSocialAgents() + " selfish agents: " + endOfDayData.numOfSelfishAgents());
                    }

                    extractTakeoverData(endOfDayData, false);
                }

                if (currentDayAfterTakeover == config.getNumOfAdditionalDaysAfterTakeover()) {
                    extractTakeoverData(endOfDayData, true);

                    // TODO: print run stats here (maybe in a method)
                    // - days it took
                    // - what kind of takeover was it
                    // - average satisfaction?
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

                    if (currentSimulationRun == config.getNumOfSimulationRuns()) {
                        writeSimulationData();

                        if (shouldShutEnvironmentDown()) {
                            // Broadcast the Terminate message to all other agents
                            AgentHelper.sendMessage(
                                    myAgent,
                                    allAgents,
                                    "Terminate",
                                    ACLMessage.INFORM
                            );

                            outputInstance.closeAllDataWriters();

                            // Terminate the ticker agent itself
                            myAgent.doDelete();
                        } else {
                            simulationReset();
                            currentSimulationSet++;

                            blockchainReference.resetBlockchain();

                            myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                            myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                        }
                    } else {
                        currentSimulationRun++;
                        runReset();

                        config.incrementRandomSeed();

                        blockchainReference.resetBlockchain();

                        myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                        myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                    }
                } else {
                    currentDay++;

                    if (takeover) {
                        currentDayAfterTakeover++;
                    }

                    // Recreate the sync behaviour and add it to the ticker's behaviour queue
                    myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                    myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
                }
            } else {
                // TODO
            }

            return 0;
        }
    }

    private void initialAgentSetup() {
        this.currentSimulationSet = 1;
        this.allAgents = new ArrayList<>();

        this.simulationReset();
        this.runReset();
    }

    private void setupSimulationSet() {
        this.simulationReset();

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

        initDataOutput();
    }

    private void initDataOutput() {
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
