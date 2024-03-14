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
import java.util.*;

public class HouseholdAgent extends Agent {
    // Agent arguments
    private AgentStrategyType agentType;
    private boolean madeInteraction;
    private ArrayList<TimeSlot> requestedTimeSlots;
    private ArrayList<TimeSlot> allocatedTimeSlots;
    private ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs;
    private HashMap<Integer, Integer> favours = new HashMap<>();
    private ArrayList<TimeSlot> exchangeRequestReceived = new ArrayList<>();
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
        initializeFavoursStore();

        AgentHelper.registerAgent(this, "Household");

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
                // Assign the ticker agent if it hasn't been assigned yet
                if (tickerAgent == null) {
                    tickerAgent = tick.getSender();
                }

                // Set up the daily tasks
                if (tick.getContent().equals("New day")) {
                    // Set the daily tracking data to their initial values at the start of the day
                    reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new FindAdvertisingBoardBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DetermineDailyDemandBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DetermineTimeSlotPreferenceBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new CalculateSlotSatisfactionBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new ReceiveRandomInitialTimeSlotAllocationBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new InitiateExchangeListenerBehaviour(myAgent));

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
            madeInteraction = false;
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

    public class InitiateExchangeListenerBehaviour extends Behaviour {
        private final SequentialBehaviour exchange = new SequentialBehaviour();
        public InitiateExchangeListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage newExchangeMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.REQUEST);

            if (newExchangeMessage != null) {
                // Define the behaviours of the exchange
                // TODO: this will require 2 sequences, one for the requester and one for the receiver
                exchange.addSubBehaviour(new AdvertiseUnwantedTimeSlotsBehaviour(myAgent));
                exchange.addSubBehaviour(new ExchangeOpenListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new TradeOfferListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new InterestResultListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new SocialCapitaSyncReceiverBehaviour(myAgent));

                myAgent.addBehaviour(exchange);
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return exchange.done();
        }

        @Override
        public int onEnd() {
            return super.onEnd();
        }

        @Override
        public void reset() {
            super.reset();
            // TODO: overwrite this with the exchange values
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

    public class DetermineDailyDemandBehaviour extends Behaviour {
        private boolean wasDailyDemandDetermined = false;

        public DetermineDailyDemandBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            // Wait until the demand indices are generated
            if (!config.getDemandCurveIndices().isEmpty()) {
                int randomDemandIndex = config.popFirstDemandCurveIndex();

                dailyDemandCurve = config.getBucketedDemandCurves()[randomDemandIndex];
                dailyDemandValue = config.getTotalDemandValues()[randomDemandIndex];

                wasDailyDemandDetermined = true;
            }
        }

        @Override
        public boolean done() {
            return wasDailyDemandDetermined;
        }
    }

    public class DetermineTimeSlotPreferenceBehaviour extends OneShotBehaviour {
        public DetermineTimeSlotPreferenceBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            // TODO: Cite Arena code
            if (!requestedTimeSlots.isEmpty()) {
                requestedTimeSlots.clear();
            }

            for (int i = 1; i <= config.getNumOfSlotsPerAgent(); i++) {
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
            timeSlotSatisfactionPairs = AgentHelper.calculateSatisfactionPerSlot(requestedTimeSlots);
        }
    }

    public class ReceiveRandomInitialTimeSlotAllocationBehaviour extends Behaviour {
        private boolean wasInitialAllocationReceived = false;

        public ReceiveRandomInitialTimeSlotAllocationBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage incomingAllocationMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.INFORM);

            if (incomingAllocationMessage != null) {
                // Make sure the incoming object is readable
                Serializable incomingObject = null;

                try {
                    incomingObject = incomingAllocationMessage.getContentObject();
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming random allocation is unreadable: " + e.getMessage());
                }

                if (incomingObject != null) {
                    // Make sure the incoming object is of the expected type
                    if (incomingObject instanceof SerializableTimeSlotArray) {
                        allocatedTimeSlots = new ArrayList<>(Arrays.asList(((SerializableTimeSlotArray)incomingObject).timeSlots()));
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Initial random allocation cannot be set: the received object has an incorrect type.");
                    }
                }

                wasInitialAllocationReceived = true;
            }
        }

        @Override
        public boolean done() {
            return wasInitialAllocationReceived;
        }
    }

    public class AdvertiseUnwantedTimeSlotsBehaviour extends Behaviour {
        private boolean isAdPosted = false;

        public AdvertiseUnwantedTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (!allocatedTimeSlots.isEmpty()) {
                // Get the difference of the allocated timeslots and the requested timeslots
                ArrayList<TimeSlot> unwantedTimeSlots = new ArrayList<>(allocatedTimeSlots);
                unwantedTimeSlots.removeAll(requestedTimeSlots);

                // Convert the list to an array
                AgentHelper.sendMessage(
                        myAgent,
                        advertisingAgent,
                        "Unwanted ad",
                        new SerializableTimeSlotArray(unwantedTimeSlots.toArray(new TimeSlot[]{})),
                        ACLMessage.REQUEST
                );

                isAdPosted = true;
            }
        }

        @Override
        public boolean done() {
            if (isAdPosted) {
                System.out.println("finished advertising");
            }

            return isAdPosted;
        }
    }

    /* Exchange Requester Behaviours */

    public class ExchangeOpenListenerBehaviour extends Behaviour {
        public ExchangeOpenListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage exchangeOpenMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.CONFIRM);

            if (exchangeOpenMessage != null) {
                if (!madeInteraction) {
                    // Get the difference of the requested timeslots and the allocated timeslots
                    ArrayList<TimeSlot> desiredTimeSlots = new ArrayList<>(requestedTimeSlots);
                    desiredTimeSlots.removeAll(allocatedTimeSlots);

                    // Send a message of interest to the advertising agent
                    AgentHelper.sendMessage(
                            myAgent,
                            advertisingAgent,
                            "Timeslots Wanted",
                            new SerializableTimeSlotArray(desiredTimeSlots.toArray(new TimeSlot[]{})),
                            ACLMessage.CFP
                    );

                    madeInteraction = true;
                }
            }
        }

        @Override
        public boolean done() {
            if (madeInteraction) {
                System.out.println("finished inquiring");
            }

            return madeInteraction; // TODO: probably incorrect
        }
    }

    public class InterestResultListenerBehaviour extends Behaviour { // TODO: decide when to call and remove these cyclic behaviours
        private boolean resultReceived = false;

        public InterestResultListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage interestResultMessage = AgentHelper.receiveCFPReply(myAgent);

            if (interestResultMessage != null && interestResultMessage.getSender().equals(advertisingAgent)) {
                if (interestResultMessage.getPerformative() == ACLMessage.AGREE) {
                    // Make sure the incoming object is readable
                    Serializable incomingObject = null;

                    try {
                        incomingObject = interestResultMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming interest result trade offer is unreadable: " + e.getMessage());
                    }

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof TradeOffer) {
                            // The content of the incoming message is built on the following scheme: "Household-1,true", where
                            // The first element of the message is the local name of the other party of the exchange
                            // The second element of the message carries the information whether the requesting party should lose social capita following the trade
                            String[] splitMessageContent = interestResultMessage.getContent().split(",");

                            boolean doesReceiverGainSocialCapita = completeRequestedExchange((TradeOffer) incomingObject);

                            // Adjust the agent's properties based on the trade offer
                            if (Boolean.parseBoolean(splitMessageContent[1])) {
                                totalSocialCapital--;
                            }

                            AgentHelper.sendMessage(
                                    myAgent,
                                    advertisingAgent,
                                    Boolean.toString(doesReceiverGainSocialCapita),
                                    ((TradeOffer) incomingObject).receiverAgent(),
                                    ACLMessage.PROPAGATE
                            );
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be handled: the received object has an incorrect type.");
                        }
                    }
                } else if (interestResultMessage.getPerformative() == ACLMessage.CANCEL) {
                    numOfDailyRejectedRequestedExchanges++;
                    // TODO: exchange over message here
                } else {
                    // TODO: let the agent's interest be refused
                    // TODO: exchange over message here
                }

                resultReceived = true;
            }
        }

        @Override
        public boolean done() {
            if (resultReceived) {
                System.out.println("finished listening to the result of the interest");
            }

            return resultReceived;
        }
    }

    /* Exchange Receiver Behaviours */

    public class TradeOfferListenerBehaviour extends Behaviour {
        private boolean offerConsidered = false;
        public TradeOfferListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tradeOfferMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.PROPOSE);

            if (tradeOfferMessage != null) {
                // Make sure the incoming object is readable
                Serializable incomingObject = null;
                int responsePerformative = ACLMessage.REJECT_PROPOSAL;

                try {
                    incomingObject = tradeOfferMessage.getContentObject();
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming trade offer is unreadable: " + e.getMessage());
                }

                boolean doesRequesterLoseSocialCapita = false;

                if (incomingObject != null) {
                    // Make sure the incoming object is of the expected type
                    if (incomingObject instanceof TradeOffer) {
                        // Consider the trade offer AND
                        // Check whether the requested time slot is actually owned by the agent
                        if (considerRequest((TradeOffer)incomingObject) && allocatedTimeSlots.contains(((TradeOffer)incomingObject).timeSlotRequested())) {
                            // Adjust the agent's properties based on the trade offer
                            doesRequesterLoseSocialCapita = completeReceivedExchange((TradeOffer)incomingObject);
                            responsePerformative = ACLMessage.ACCEPT_PROPOSAL;
                        }

                        AgentHelper.sendMessage(
                                myAgent,
                                advertisingAgent,
                                Boolean.toString(doesRequesterLoseSocialCapita),
                                incomingObject,
                                responsePerformative
                        );

                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be answered: the received object has an incorrect type.");
                    }
                }

                offerConsidered = true;
            }
        }

        @Override
        public boolean done() {
            offerConsidered = true;

            if (offerConsidered) {
                System.out.println("finished considering the offer");
            }

            return offerConsidered;
        }
    }

    public class SocialCapitaSyncReceiverBehaviour extends Behaviour {
        private boolean socialCapitaSynced = false;
        public SocialCapitaSyncReceiverBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.INFORM_IF);

            if (incomingSyncMessage != null) {
                if (incomingSyncMessage.getContent() != null) {
                    boolean doesReceiverAgentGainSocialCapita = Boolean.parseBoolean(incomingSyncMessage.getContent());

                    if (doesReceiverAgentGainSocialCapita) {
                        totalSocialCapital++;
                    }
                }

                socialCapitaSynced = true;

                // TODO: exchange over message here
            }
        }

        @Override
        public boolean done() {
            socialCapitaSynced = true;

            if (socialCapitaSynced) {
                System.out.println("finished syncing");
            }

            return socialCapitaSynced;
        }
    }

    // TODO: Cite Arena code
    /**
     * Identifies all other Agents in the ExchangeArena and initialises counts of favours given to and received from
     * each other Agent.
     */
    private void initializeFavoursStore() {
        if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
            if (!this.favours.isEmpty()) {
                this.favours.clear();
            }

            for (int i = 1; i <= RunConfigurationSingleton.getInstance().getPopulationCount(); i++) {
                if (AgentHelper.getHouseholdAgentNumber(this.getLocalName()) != i) {
                    // Initially, no favours are owed or have been given to any other agent.
                    // The key is the other agent's household number.
                    this.favours.put(i, 0);
                }
            }
        }
    }

    // TODO: Cite Arena code
    /**
     * Determine whether the Agent will be willing to accept a received exchange request.
     *
     * @return Boolean Whether the request was accepted.
     */
    private boolean considerRequest(TradeOffer offer) {
        boolean exchangeRequestApproved = false;
        double currentSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Create a new local list of time-slots in order to test how the Agents satisfaction would change after the
        // potential exchange.
        ArrayList<TimeSlot> potentialAllocatedTimeSlots = new ArrayList<>(allocatedTimeSlots);

        // Check this Agent still has the time-slot requested by attempting to remove it.
        if (potentialAllocatedTimeSlots.remove(offer.timeSlotRequested())) {
            // Replace the requested slot with the requesting agents unwanted time-slot.
            potentialAllocatedTimeSlots.add(offer.timeSlotOffered());

            double potentialSatisfaction = AgentHelper.calculateSatisfaction(potentialAllocatedTimeSlots, this.requestedTimeSlots);

            if (this.agentType == AgentStrategyType.SOCIAL) {
                // Social Agents accept offers that improve their satisfaction or if they have negative social capital
                // with the Agent who made the request.
                if (Double.compare(potentialSatisfaction, currentSatisfaction) > 0) {
                    exchangeRequestApproved = true;
                    this.numOfDailyExchangesWithoutSocialCapital++;
                } else if (Double.compare(potentialSatisfaction, currentSatisfaction) == 0) {
                    if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
                        if (favours.get(AgentHelper.getHouseholdAgentNumber(offer.senderAgent().getLocalName())) < 0) {
                            exchangeRequestApproved = true;
                            this.numOfDailyExchangesWithSocialCapital++;
                        }
                    } else {
                        // When social capital isn't used, social agents always accept neutral exchanges.
                        exchangeRequestApproved = true;
                        this.numOfDailyExchangesWithoutSocialCapital++;
                    }
                }
            } else {
                // Selfish Agents and Agents with no known type use the default selfish approach.
                // Selfish Agents only accept offers that improve their individual satisfaction.
                if (Double.compare(potentialSatisfaction, currentSatisfaction) > 0) {
                    exchangeRequestApproved = true;
                    this.numOfDailyExchangesWithoutSocialCapital++;
                }
            }

            if (!exchangeRequestApproved) {
                this.numOfDailyRejectedReceivedExchanges++;
            }
        }

        return exchangeRequestApproved;
    }

    // TODO: Cite Arena code
    /**
     * Completes an exchange that was originally requested by another Agent, making the exchange and updating this
     * Agents relationship with the other Agent involved.
     *
     * @param offer The exchange that is to be completed.
     * @return Boolean Whether the other agent gained social capital.
     */
    private boolean completeReceivedExchange(TradeOffer offer) {
        boolean otherAgentSCLoss = false;

        double previousSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Update the Agents allocated time-slots.
        this.allocatedTimeSlots.remove(offer.timeSlotRequested());
        this.allocatedTimeSlots.add(offer.timeSlotOffered());

        double newSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);

        // Update the Agents relationship with the other Agent involved in the exchange.
        if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
            if (Double.compare(newSatisfaction, previousSatisfaction) <= 0 && this.agentType == AgentStrategyType.SOCIAL) {
                int otherHouseholdAgentNumber = AgentHelper.getHouseholdAgentNumber(offer.senderAgent().getLocalName());
                this.favours.replace(otherHouseholdAgentNumber, this.favours.get(otherHouseholdAgentNumber) + 1);

                otherAgentSCLoss = true;
            }
        }

        return otherAgentSCLoss;
    }

    // TODO: Cite Arena code
    /**
     * Completes an exchange that was originally requested by this Agent, making the exchange and updating this Agents
     * relationship with the other Agent involved.
     *
     * @param offer The exchange that is to be completed.
     * @return Boolean Whether the other agent gained social capital.
     */
    boolean completeRequestedExchange(TradeOffer offer) {
        boolean otherAgentSCGain = false;

        double previousSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Update the Agents allocated time-slots.
        this.allocatedTimeSlots.remove(offer.timeSlotOffered());
        this.allocatedTimeSlots.add(offer.timeSlotRequested());

        double newSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);

        // Update the Agents relationship with the other Agent involved in the exchange.
        if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
            if (Double.compare(newSatisfaction, previousSatisfaction) > 0 && this.agentType == AgentStrategyType.SOCIAL) {
                int otherHouseholdAgentNumber = AgentHelper.getHouseholdAgentNumber(offer.receiverAgent().getLocalName());
                this.favours.replace(otherHouseholdAgentNumber, this.favours.get(otherHouseholdAgentNumber) - 1);

                otherAgentSCGain = true;
            }
        }

        this.numOfDailyAcceptedRequestedExchanges++;

        return otherAgentSCGain;
    }
}
