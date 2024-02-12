package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;

public class TickerAgent extends Agent {
    // TODO: figure out how to import the max number of days as a property
    // configuration properties class?
    // global variable
    private final int maxNumOfDays = 30;

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
        private int day = 0;
        private ArrayList<AID> householdAgents;
        private AID advertisingAgent;

        public DailySyncBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            switch (step) {
                case 0:
                    householdAgents = AgentHelper.saveAgentContacts(myAgent, "Household");
                    advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst();

                    ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
                    tick.setContent("New day");

                    for (AID id : householdAgents) {
                        tick.addReceiver(id);
                    }

                    tick.addReceiver(advertisingAgent);

                    myAgent.send(tick);
                    step++;
                    day++;

                    break;
                case 1:
                    MessageTemplate mt = MessageTemplate.MatchContent("Done");
                    ACLMessage msg = myAgent.receive(mt);

                    if (msg != null) {
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
        public void reset() {
            super.reset();
            step = 0;
            householdAgents.clear();
            numFinReceived = 0;
        }

        @Override
        public int onEnd() {
            System.out.println("End of day " + day);

            if (day == maxNumOfDays) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent("Terminate");

                for (AID agent : householdAgents) {
                    msg.addReceiver(agent);
                }

                msg.addReceiver(advertisingAgent);

                myAgent.send(msg);
                myAgent.doDelete();
            } else {
                reset();
                myAgent.addBehaviour(this);
            }

            return 0;
        }
    }
}
