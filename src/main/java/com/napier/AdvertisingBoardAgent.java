package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdvertisingBoardAgent extends Agent {
    private ArrayList<TimeSlot> availableTimeSlots;
    private ArrayList<Advert> adverts;

    // Agent contact attributes
    private AID tickerAgent;
    private ArrayList<AID> householdAgents;

    @Override
    protected void setup() {
        availableTimeSlots = new ArrayList<>();
        adverts = new ArrayList<>();

        AgentHelper.registerAgent(this, "Advertising-board");

        addBehaviour(new TickerDailyBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
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
                    dailyTasks.addSubBehaviour(new DistributeInitialRandomTimeSlotAllocations(myAgent));

                    myAgent.addBehaviour(dailyTasks);
                    myAgent.addBehaviour(new UnwantedTimeSlotsAdvertListenerBehaviour(myAgent));
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

            AgentHelper.printAgentLog(myAgent.getLocalName(), "Time Slots generated: " + availableTimeSlots.size());
            Collections.shuffle(householdAgents, config.getRandom());
        }
    }

    public class DistributeInitialRandomTimeSlotAllocations extends OneShotBehaviour {
        public DistributeInitialRandomTimeSlotAllocations(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            for (AID householdAgent : householdAgents) {
                // TODO: Cite Arena code
                TimeSlot[] initialTimeSlots = new TimeSlot[config.getNumOfSlotsPerAgent()];

                // TODO: find out: is the number of requested time slots == the number of slots per agent?
                for (int i = 0; i < config.getNumOfSlotsPerAgent(); i++) {
                    // Only allocate time-slots if there are slots available to allocate.
                    if (!availableTimeSlots.isEmpty()) {
                        int selector = config.getRandom().nextInt(availableTimeSlots.size());
                        TimeSlot timeSlot = availableTimeSlots.get(selector);

                        initialTimeSlots[i] = timeSlot;
                        availableTimeSlots.remove(selector);
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Error: No Time-Slots Available");
                    }
                }

                // Send the initial allocation to the given household
                AgentHelper.sendMessage(
                        myAgent,
                        householdAgent,
                        "Initial Allocation Enclosed.",
                        new SerializableTimeSlotArray(initialTimeSlots),
                        ACLMessage.INFORM
                );
            }
        }
    }

    public class UnwantedTimeSlotsAdvertListenerBehaviour extends CyclicBehaviour {
        private int numOfAdvertsReceived = 0;

        public UnwantedTimeSlotsAdvertListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage advertisingMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.REQUEST);

            if (advertisingMessage != null) {
                try {
                    Serializable incomingObject = advertisingMessage.getContentObject();

                    // Make sure the incoming object is of the expected type and the advert is not empty
                    if (incomingObject instanceof SerializableTimeSlotArray) {
                        if (((SerializableTimeSlotArray) incomingObject).timeSlots().length > 0) {
                            // Register the advert
                            adverts.add(new Advert(
                                    advertisingMessage.getSender(),
                                    ((SerializableTimeSlotArray) incomingObject).timeSlots())
                            );
                        }

                        numOfAdvertsReceived++;
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Advert cannot be registered: the received object has an incorrect type.");
                    }
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming advert message is unreadable: " + e.getMessage());
                }

                if (numOfAdvertsReceived == householdAgents.size()) {
                    myAgent.removeBehaviour(this);
                }
            } else {
                block();
            }
        }
    }

    // if not made interaction yet, make a request for an exchange by selecting a slot from the available ones
    //

    public class CallItADayListenerBehaviour extends CyclicBehaviour {
        private int householdsDayOver = 0;
        private final List<Behaviour> behavioursToRemove;

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
