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
    private List<SlotSatisfactionPair> timeslotSatisfactionPairs;

    private HashMap<AID, Integer> favours = new HashMap<>();
    private ArrayList<Integer> exchangeRequestReceived = new ArrayList<>();
    private boolean isExchangeRequestApproved;
    private int totalSocialCapital;
    private int numOfDailyExchangesWithSocialCapital;
    private int numOfDailyExchangesWithoutSocialCapital;
    private int numOfDailyRejectedReceivedExchanges;
    private int numOfDailyRejectedRequestedExchanges;
    private int numOfDailyAcceptedRequestedExchanges;
    private ArrayList<Integer> unallocatedCurveIndices;
    private double[] dailyDemandCurve = new double[RunConfigurationSingleton.getInstance().getBucketedDemandCurves().length];

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
        this.timeslotSatisfactionPairs = new ArrayList<>();
        this.totalSocialCapital = 0;
        this.numOfDailyExchangesWithSocialCapital = 0;
        this.numOfDailyExchangesWithoutSocialCapital = 0;
        this.numOfDailyRejectedReceivedExchanges = 0;
        this.numOfDailyRejectedRequestedExchanges = 0;
        this.numOfDailyAcceptedRequestedExchanges = 0;
        this.unallocatedCurveIndices = new ArrayList<>();

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
            unallocatedCurveIndices.clear();
            requestedTimeSlots.clear();
            allocatedTimeSlots.clear();
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

            dailyDemandCurve = config.getBucketedDemandCurves()[config.popFirstDemandCurveIndex()];
        }
    }

    public class RequestInitialSlotsBehaviour extends OneShotBehaviour {
        public RequestInitialSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            // we need: bucketedDemandCurves[selector], totalDemandValues[selector]
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
