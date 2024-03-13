package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

public class TickerAgent extends Agent {
    // TODO: figure out how to import the max number of days as a property
    // there is no max number of days, the simulation runs until a takeover is achieved
    // configuration properties class?
    // global variable
    private final int maxNumOfDays = 30;
    private int currentDay = 0;

    // Agent contact attributes
    private ArrayList<AID> allAgents;

    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "Ticker");

        allAgents = new ArrayList<>();

        doWait(1500);
        addBehaviour(new DailySyncBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);
    }

    public class DailySyncBehaviour extends Behaviour {
        private int step = 0;
        private ArrayList<AID> householdAgents;
        private AID advertisingAgent;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // TODO: Cite JADE workbook for the step logic
            switch (step) {
                case 0:
                    // Find all Household and Advertising-board agents
                    householdAgents = AgentHelper.saveAgentContacts(myAgent, "Household");
                    advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst();

                    // Collect all receivers
                    if (allAgents.size() <= RunConfigurationSingleton.getInstance().getPopulationCount()) {
                        allAgents.clear();
                        allAgents.addAll(householdAgents);
                        allAgents.add(advertisingAgent);
                    }

                    // Broadcast the start of the new day to other agents
                    AgentHelper.sendMessage(myAgent, allAgents, "New day", ACLMessage.INFORM);

                    // Progress the agent state
                    step++;
                    currentDay++;

                    break;
                case 1:
                    if (AgentHelper.receiveMessage(myAgent, "Done") != null) {
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
            AgentHelper.printAgentLog(myAgent.getLocalName(), "End of day " + currentDay);

            // Reshuffle the daily demand curve allocation
            RunConfigurationSingleton.getInstance().recreateDemandCurveIndices();

            if (currentDay == maxNumOfDays) {
                // Broadcast the Terminate message to all other agents
                AgentHelper.sendMessage(myAgent, allAgents, "Terminate", ACLMessage.INFORM);

                // Terminate the ticker agent itself
                myAgent.doDelete();
            } else {
                // Recreate the sync behaviour and add it to the ticker's behaviour queue
                myAgent.addBehaviour(new DailySyncBehaviour(myAgent));
            }

            return 0;
        }
    }
}
