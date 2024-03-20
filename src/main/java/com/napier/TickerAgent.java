package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.io.Serializable;
import java.util.ArrayList;

public class TickerAgent extends Agent {
    private int currentSimulationRun = 1;
    private int currentDay = 1;
    private int currentDayAfterTakeover = 0;
    private boolean takeover = false;

    // Agent contact attributes
    private ArrayList<AID> allAgents;
    private ArrayList<AgentContact> householdAgentContacts;
    private AID advertisingAgent;

    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "Ticker");

        allAgents = new ArrayList<>();

        doWait(1500);
        addBehaviour(new FindHouseholdsBehaviour(this));
        addBehaviour(new FindAdvertisingBoardBehaviour(this));
        addBehaviour(new DailySyncBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);
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
        private int numOfSocialAgents = 0;
        private int numOfSelfishAgents = 0;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

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

                    if (currentDay == 1) {
                        AgentHelper.sendMessage(
                                myAgent,
                                allAgents,
                                "New Run",
                                ACLMessage.INFORM
                        );
                    } else {
                        // Broadcast the start of the new day to other agents
                        AgentHelper.sendMessage(
                                myAgent,
                                allAgents,
                                "New Day",
                                ACLMessage.INFORM
                        );
                    }

                    // Progress the agent state
                    step++;

                    break;
                case 1:
                    ACLMessage advertisingDayOverMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, "Done", ACLMessage.INFORM);

                    if (advertisingDayOverMessage != null) {
                        // Make sure the incoming object is readable
                        Serializable incomingObject = null;

                        try {
                            incomingObject = advertisingDayOverMessage.getContentObject();
                        } catch (UnreadableException e) {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Agent contact list is unreadable: " + e.getMessage());
                        }

                        if (incomingObject != null) {
                            // Make sure the incoming object is of the expected type
                            if (incomingObject instanceof SerializableAgentContactList) {
                                // Overwrite the existing list of contacts with the updated list
                                householdAgentContacts = ((SerializableAgentContactList)incomingObject).contacts();
                            }
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Agent contact list was not updated: the received object has an incorrect type.");
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
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "End of day " + currentDay);
            }

            // TODO: Cite Arena code
            for (AgentContact householdAgentContact : householdAgentContacts) {
                if (householdAgentContact.getType() == AgentStrategyType.SOCIAL) {
                    numOfSocialAgents++;
                } else if (householdAgentContact.getType() == AgentStrategyType.SELFISH) {
                    numOfSelfishAgents++;
                }
            }

            // TODO: Cite Arena code
            if (((numOfSelfishAgents == 0 || numOfSocialAgents == 0) || config.getNumOfAgentsToEvolve() == 0) && !takeover) {
                takeover = true;

                if (config.isDebugMode()) {
                    AgentHelper.printAgentLog(myAgent.getLocalName(), "takeover! social agents: " + numOfSocialAgents + " selfish agents: " + numOfSelfishAgents);
                }
            }

            if (currentDayAfterTakeover == config.getAdditionalDays()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), currentSimulationRun + "/" + config.getNumOfSimulationRuns() + " runs ended.");
                // TODO: print run stats here (maybe in a method)
                // - days it took
                // - what kind of takeover was it
                // - average satisfaction?
                AgentStrategyType takeoverType;

                if (numOfSelfishAgents == 0) {
                    takeoverType = AgentStrategyType.SOCIAL;
                } else {
                    takeoverType = AgentStrategyType.SELFISH;
                }

                AgentHelper.printAgentLog(myAgent.getLocalName(), "Days: " + currentDay + ", Takeover: " + takeoverType + ", Average satisfaction: ");

                if (currentSimulationRun == config.getNumOfSimulationRuns()) {
                    // Broadcast the Terminate message to all other agents
                    AgentHelper.sendMessage(
                            myAgent,
                            allAgents,
                            "Terminate",
                            ACLMessage.INFORM
                    );

                    // Terminate the ticker agent itself
                    myAgent.doDelete();
                } else {
                    currentSimulationRun++;
                    initialAgentSetup();

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

            return 0;
        }
    }

    private void initialAgentSetup() {
        this.currentDay = 1;
        this.currentDayAfterTakeover = 0;
        this.takeover = false;
    }
}
