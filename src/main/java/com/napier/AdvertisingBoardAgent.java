package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.List;

public class AdvertisingBoardAgent extends Agent {
    private AID tickerAgent;
    private ArrayList<AID> householdAgents;

    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "Advertising-board");

        addBehaviour(new TickerDailyBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.logActivity(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);
    }

    public class TickerDailyBehaviour extends CyclicBehaviour {
        public TickerDailyBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tick = AgentHelper.receiveMessage(myAgent, "New day", "Terminate");

            if (tick != null) {
                if (tickerAgent == null) {
                    tickerAgent = tick.getSender();
                }

                if (tick.getContent().equals("New day")) {
                    myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));

                    ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    // TODO: Add sub-behaviours here
                    dailyTasks.addSubBehaviour(new CallItADayListenerBehaviour(myAgent, cyclicBehaviours));

                    myAgent.addBehaviour(dailyTasks);
                } else {
                    myAgent.doDelete();
                }
            } else {
                block();
            }
        }
    }

    public class FindHouseholdsBehaviour extends OneShotBehaviour {
        public FindHouseholdsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            householdAgents = AgentHelper.saveAgentContacts(myAgent, "Household");
        }
    }

    public class CallItADayListenerBehaviour extends CyclicBehaviour {
        private int householdsDayOver = 0;
        private List<Behaviour> behavioursToRemove;

        public CallItADayListenerBehaviour(Agent a, List<Behaviour> behavioursToRemove) {
            super(a);
            this.behavioursToRemove = behavioursToRemove;
        }

        @Override
        public void action() {
            System.out.println("1");
            System.out.println(myAgent.getLocalName());
            ACLMessage doneMessage = AgentHelper.receiveMessage(myAgent, "Done");

            if (doneMessage != null) {
                householdsDayOver++;
            } else {
                block();
            }

            if (householdsDayOver == householdAgents.size()) {
                AgentHelper.sendMessage(myAgent, tickerAgent, "Done", ACLMessage.INFORM);

                for (Behaviour behaviour : behavioursToRemove) {
                    myAgent.removeBehaviour(behaviour);
                }

                myAgent.removeBehaviour(this);
            }
        }
    }
}
