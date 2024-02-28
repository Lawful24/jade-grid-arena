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
    private ArrayList<TimeSlot> availableTimeSlots;
    private AID tickerAgent;
    private ArrayList<AID> householdAgents;

    @Override
    protected void setup() {
        availableTimeSlots = new ArrayList<>();
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
                    dailyTasks.addSubBehaviour(new GenerateTimeSlotsBehaviour(myAgent));

                    myAgent.addBehaviour(dailyTasks);
                    myAgent.addBehaviour(new CallItADayListenerBehaviour(myAgent, cyclicBehaviours));
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

    public class GenerateTimeSlotsBehaviour extends OneShotBehaviour {
        public GenerateTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            availableTimeSlots.clear();

            // TODO: Cite Arena code
            // Fill the available time-slots with all the slots that exist each day.
            int numOfRequiredTimeSlots = config.getPopulationCount() * config.getNumOfSlotsPerAgent();

            for (int i = 1; i <= numOfRequiredTimeSlots; i++) {
                // Selects a time-slot based on the demand curve.
                int wheelSelector = RunConfigurationSingleton.getInstance().getRandom().nextInt(config.getTotalAvailableEnergy());
                int wheelCalculator = 1; // if we start at 0, there will be 25 potential time slots in a day instead of 24
                int timeSlotStart = 0;

                while (wheelCalculator < wheelSelector) {
                    wheelCalculator = wheelCalculator + (config.getBucketedAvailabilityCurve()[timeSlotStart]);
                    timeSlotStart++;
                }

                availableTimeSlots.add(new TimeSlot(timeSlotStart));
            }

            AgentHelper.logActivity(myAgent.getLocalName(), "Time Slots generated: " + availableTimeSlots.size());
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
            // TODO: Cite JADE workbook
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
