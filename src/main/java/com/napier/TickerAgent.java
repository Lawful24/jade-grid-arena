package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

public class TickerAgent extends Agent {
    // TODO: figure out how to import the max number of days as a property
    // configuration properties class?
    // global variable
    private final int maxNumOfDays = 30;
    private int currentDay = 0;

    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "Ticker");

        doWait(2500);
        addBehaviour(new DailySyncBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.deregisterAgent(this);
    }

    public class DailySyncBehaviour extends Behaviour {
        private int step = 0;
        private int numFinReceived = 0;
        //private int day = 0;
        private ArrayList<AID> householdAgents;
        private AID advertisingAgent;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            switch (step) {
                case 0:
                    // Find all Household and Advertising-board agents
                    householdAgents = AgentHelper.saveAgentContacts(myAgent, "Household");
                    //advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst();

                    // Collect all receivers
                    ArrayList<AID> receivers = new ArrayList<>(householdAgents);
                    //receivers.add(advertisingAgent);

                    // Broadcast the start of the new day to other agents
                    AgentHelper.sendMessage(myAgent, receivers, "New day", ACLMessage.INFORM);

                    // Progress the agent state
                    step++;
                    currentDay++;

                    break;
                case 1:
                    doWait(1000);
                    if (AgentHelper.receiveMessage(myAgent, "Done") != null) {
                        numFinReceived++;

                        if (numFinReceived >= householdAgents.size()) {
                            step++;
                        }
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
            AgentHelper.logActivity(myAgent.getLocalName(), "End of day " + currentDay);

            if (currentDay == maxNumOfDays) {
                // Collect all receivers
                ArrayList<AID> receivers = new ArrayList<>(householdAgents);
                receivers.add(advertisingAgent);

                // Broadcast the Terminate message to all other agents
                AgentHelper.sendMessage(myAgent, receivers, "Terminate", ACLMessage.INFORM);

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
