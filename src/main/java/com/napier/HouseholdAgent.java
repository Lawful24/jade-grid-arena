package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class HouseholdAgent extends Agent {
    // Agent arguments
    private AgentStrategyType agentType;
    private final boolean utilisesSocialCapital = RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital();
    private boolean madeInteraction;
    private final int numOfTimeSlotsWanted = RunConfigurationSingleton.getInstance().getNumOfSlotsPerAgent();
    private final int numOfUniqueTimeSlots = RunConfigurationSingleton.getInstance().getNumOfUniqueTimeSlots();
    private ArrayList<TimeSlot> requestedTimeSlots;
    private ArrayList<TimeSlot> allocatedTimeSlots;
    private final double[] satisfactionCurve = RunConfigurationSingleton.getInstance().getSatisfactionCurve();
    private ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs;
    private HashMap<AID, Integer> favours = new HashMap<>();
    private ArrayList<Integer> exchangeRequestReceived = new ArrayList<>();
    private boolean isExchangeRequestApproved;
    private int totalSocialCapital;
    private int numOfDailyExchangesWithSocialCapital;
    private int numOfDailyExchangesWithoutSocialCapital;
    private int numOfDailyRejectedReceivedExchanges;
    private int numOfDailyRejectedRequestedExchanges;
    private int numOfDailyAcceptedRequestedExchanges;
    private double[] dailyDemandCurve = new double[RunConfigurationSingleton.getInstance().getBucketedDemandCurves().length];
    private double dailyDemandValue;

    // Agent contact attributes
    private AID tickerAgent;
    private AID advertisingAgent;

    @Override
    protected void setup() {
        // Import the arguments
        this.agentType = (AgentStrategyType)getArguments()[0];

        // Initialise local attributes
        this.madeInteraction = false;
        this.requestedTimeSlots = new ArrayList<>();
        this.allocatedTimeSlots = new ArrayList<>();
        this.timeSlotSatisfactionPairs = new ArrayList<>();
        this.totalSocialCapital = 0;
        this.numOfDailyExchangesWithSocialCapital = 0;
        this.numOfDailyExchangesWithoutSocialCapital = 0;
        this.numOfDailyRejectedReceivedExchanges = 0;
        this.numOfDailyRejectedRequestedExchanges = 0;
        this.numOfDailyAcceptedRequestedExchanges = 0;

        AgentHelper.registerAgent(this, "Household");

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
                // Assign the ticker agent if it hasn't been assigned yet
                if (tickerAgent == null) {
                    tickerAgent = tick.getSender();
                }

                // Set up the daily tasks
                if (tick.getContent().equals("New day")) {
                    // Set the daily tracking data to their initial values at the start of the day
                    reset();

                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    // TODO: Add sub-behaviours here
                    dailyTasks.addSubBehaviour(new DetermineDailyDemandBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DetermineInitialRequestedTimeSlotsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new CalculateSlotSatisfactionBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new CallItADayBehaviour(myAgent));

                    myAgent.addBehaviour(new FindAdvertisingBoardBehaviour(myAgent));
                    myAgent.addBehaviour(dailyTasks);
                } else {
                    myAgent.doDelete();
                }
            } else {
                block();
            }
        }

        @Override
        public void reset() {
            super.reset();
            numOfDailyExchangesWithSocialCapital = 0;
            numOfDailyExchangesWithoutSocialCapital = 0;
            numOfDailyRejectedReceivedExchanges = 0;
            numOfDailyRejectedRequestedExchanges = 0;
            numOfDailyAcceptedRequestedExchanges = 0;
            requestedTimeSlots.clear();
            allocatedTimeSlots.clear();
            timeSlotSatisfactionPairs.clear();
        }
    }

    public class FindAdvertisingBoardBehaviour extends OneShotBehaviour {
        public FindAdvertisingBoardBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            advertisingAgent = AgentHelper.saveAgentContacts(myAgent, "Advertising-board").getFirst();
        }
    }

    public class DetermineDailyDemandBehaviour extends OneShotBehaviour {
        public DetermineDailyDemandBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
            int randomDemandIndex = config.popFirstDemandCurveIndex();

            dailyDemandCurve = config.getBucketedDemandCurves()[randomDemandIndex];
            dailyDemandValue = config.getTotalDemandValues()[randomDemandIndex];
        }
    }

    public class DetermineInitialRequestedTimeSlotsBehaviour extends OneShotBehaviour {
        public DetermineInitialRequestedTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            // TODO: Cite Arena code
            if (!requestedTimeSlots.isEmpty()) {
                requestedTimeSlots.clear();
            }

            for (int i = 1; i <= numOfTimeSlotsWanted; i++) {
                // Selects a time-slot based on the demand curve.
                int wheelSelector = config.getRandom().nextInt((int)(dailyDemandValue * 10)) + 1;
                int wheelCalculator = 0;
                int timeSlotStart = 0;

                while (wheelCalculator < wheelSelector) {
                    wheelCalculator = wheelCalculator + ((int)(dailyDemandCurve[timeSlotStart] * 10));
                    timeSlotStart++;
                }

                TimeSlot timeSlotToAdd = new TimeSlot(timeSlotStart);

                if (requestedTimeSlots.contains(timeSlotToAdd)) {
                    i--;
                } else {
                    requestedTimeSlots.add(timeSlotToAdd);
                }
            }
        }
    }

    public class CalculateSlotSatisfactionBehaviour extends OneShotBehaviour {
        public CalculateSlotSatisfactionBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // TODO: Cite Arena code
            // Calculate the potential satisfaction that each time-slot could give based on their proximity to requested time-slots.
            Double[] slotSatisfaction = new Double[RunConfigurationSingleton.getInstance().getNumOfUniqueTimeSlots()];
            Arrays.fill(slotSatisfaction, 0.0);

            for (TimeSlot ts : requestedTimeSlots) {
                int slotSatisfactionIndex = ts.getStartHour() - 1;
                slotSatisfaction[slotSatisfactionIndex] = satisfactionCurve[0];

                // Apply the adjustment values to neighboring elements
                for (int i = 1; i < satisfactionCurve.length; i++) {
                    int leftIndex = slotSatisfactionIndex - i;
                    int rightIndex = slotSatisfactionIndex + i;

                    if (leftIndex < 0) {
                        leftIndex += slotSatisfaction.length;
                    }

                    if (rightIndex >= slotSatisfaction.length) {
                        rightIndex -= slotSatisfaction.length;
                    }

                    slotSatisfaction[leftIndex] = Math.max(slotSatisfaction[leftIndex], satisfactionCurve[i]);
                    slotSatisfaction[rightIndex] = Math.max(slotSatisfaction[rightIndex], satisfactionCurve[i]);
                }
            }

            for (int i = 0; i < slotSatisfaction.length; i++) {
                timeSlotSatisfactionPairs.add(new TimeSlotSatisfactionPair(new TimeSlot(i + 1), slotSatisfaction[i]));
            }
        }
    }

    public class CallItADayBehaviour extends OneShotBehaviour {
        public CallItADayBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            AgentHelper.sendMessage(myAgent, new ArrayList<>(Arrays.asList(tickerAgent, advertisingAgent)), "Done", ACLMessage.INFORM); // TODO: rework the communication between the agents
        }
    }
}
