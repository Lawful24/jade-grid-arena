package com.napier.agents;

import com.napier.AgentHelper;
import com.napier.concepts.AgentContact;
import com.napier.concepts.dataholders.EndOfExchangeHouseholdDataHolder;
import com.napier.concepts.dataholders.EndOfDayHouseholdAgentDataHolder;
import com.napier.concepts.dataholders.SerializableTimeSlotArray;
import com.napier.concepts.TimeSlot;
import com.napier.concepts.TimeSlotSatisfactionPair;
import com.napier.concepts.TradeOffer;
import com.napier.singletons.SimulationConfigurationSingleton;
import com.napier.singletons.SmartContract;
import com.napier.types.AgentStrategyType;
import com.napier.types.ExchangeType;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class HouseholdAgent extends Agent {
    // Agent arguments
    private AgentStrategyType agentType;
    private ArrayList<TimeSlot> requestedTimeSlots;
    private ArrayList<TimeSlot> allocatedTimeSlots;
    private ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs;
    private HashMap<String, Integer> favours;
    private double[] dailyDemandCurve;
    private double dailyDemandValue;
    private int numOfDailyRejectedReceivedExchanges;
    private int numOfDailyRejectedRequestedExchanges;
    private int numOfDailyAcceptedRequestedExchanges;
    private int totalSocialCapita;
    private int numOfDailyAcceptedReceivedExchangesWithSocialCapita;
    private int numOfDailyAcceptedReceivedExchangesWithoutSocialCapita;

    // Updated properties
    private boolean isTradeStarted;
    private double currentSatisfaction;
    private boolean areHouseholdsFound;
    private boolean isExchangeTypeBeingSwitched;
    private boolean activeExchange;
    private long exchangeRoundStartTime;
    private boolean isTradeOfferReceiver;

    // Agent contact attributes
    private AID tickerAgent;
    private AID advertisingAgent;

    // Singleton
    private SimulationConfigurationSingleton config;

    @Override
    protected void setup() {
        this.initialAgentSetup();

        AgentHelper.registerAgent(this, "Household");

        this.config = SimulationConfigurationSingleton.getInstance();

        addBehaviour(new FindTickerBehaviour(this));
        addBehaviour(new FindAdvertisingBoardBehaviour(this));
        addBehaviour(new TickerDailyBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);
    }

    public class FindTickerBehaviour extends OneShotBehaviour {
        public FindTickerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            tickerAgent = AgentHelper.saveAgentContacts(myAgent, "Ticker").getFirst().getAgentIdentifier();
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

    public class FindHouseholdsBehaviour extends OneShotBehaviour {
        public FindHouseholdsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            AgentHelper.saveAgentContacts(myAgent, "Household");

            areHouseholdsFound = true;
        }
    }

    public class TickerDailyBehaviour extends CyclicBehaviour {
        public TickerDailyBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tick = AgentHelper.receiveMessage(myAgent, tickerAgent, ACLMessage.INFORM);

            if (tick != null) {
                // Set up the daily tasks
                if (!tick.getConversationId().equals("Terminate")) {
                    // Do a reset on all agent attributes on each new simulation run
                    if (tick.getConversationId().equals("New Run")) {
                        initialAgentSetup();

                        if (config.isDebugMode()) {
                            AgentHelper.printAgentLog(myAgent.getLocalName(), "Reset for new run");

                            if (myAgent.getBehavioursCnt() > 2) {
                                AgentHelper.printAgentLog(getLocalName(), "behaviour count at the start of a new run: " + myAgent.getBehavioursCnt());
                            }
                        }
                    }

                    // Set the daily tracking data to their initial values at the start of the day
                    this.reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new DetermineDailyDemandBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DetermineTimeSlotPreferenceBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new CalculateSlotSatisfactionBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new ReceiveRandomInitialTimeSlotAllocationBehaviour(myAgent));
                    myAgent.addBehaviour(dailyTasks);

                    if (!activeExchange) {
                        switch (config.getExchangeType()){
                            case MessagePassing -> myAgent.addBehaviour(new InitiateExchangeListenerBehaviour(myAgent));
                            case SmartContract -> myAgent.addBehaviour(new InitiateExchangeListenerSCBehaviour(myAgent));
                        }

                        activeExchange = true;
                    }

                    myAgent.addBehaviour(new SocialLearningListenerBehaviour(myAgent));
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

            numOfDailyRejectedReceivedExchanges = 0;
            numOfDailyRejectedRequestedExchanges = 0;
            numOfDailyAcceptedRequestedExchanges = 0;
            numOfDailyAcceptedReceivedExchangesWithSocialCapita = 0;
            numOfDailyAcceptedReceivedExchangesWithoutSocialCapita = 0;
            requestedTimeSlots.clear();
            allocatedTimeSlots.clear();
            timeSlotSatisfactionPairs.clear();
        }
    }

    public class DetermineDailyDemandBehaviour extends Behaviour {
        private boolean wasDailyDemandDetermined = false;

        public DetermineDailyDemandBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
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
            ACLMessage incomingAllocationMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, "Initial Allocation Enclosed.", ACLMessage.INFORM);

            if (incomingAllocationMessage != null) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(incomingAllocationMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type
                if (receivedObject instanceof SerializableTimeSlotArray randomTimeSlotAllocationHolder) {
                    allocatedTimeSlots = new ArrayList<>(Arrays.asList(randomTimeSlotAllocationHolder.timeSlots()));
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Initial random allocation cannot be set: the received object has an incorrect type or is null.");
                }

                wasInitialAllocationReceived = true;
            }
        }

        @Override
        public boolean done() {
            return wasInitialAllocationReceived;
        }
    }

    public class InitiateExchangeListenerBehaviour extends Behaviour {
        private boolean isExchangeActive = false;
        private final SequentialBehaviour exchange = new SequentialBehaviour();
        public InitiateExchangeListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage newExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Initiated");

            if (newExchangeMessage != null || isExchangeTypeBeingSwitched) {
                if (config.getExchangeType() == ExchangeType.MessagePassing) {
                    exchangeRoundStartTime = System.nanoTime();

                    if (config.isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "joining the exchange");
                    }

                    this.reset();

                    // Define the behaviours of the exchange
                    exchange.addSubBehaviour(new AdvertiseUnwantedTimeSlotsBehaviour(myAgent));
                    exchange.addSubBehaviour(new ExchangeOpenListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new TradeOfferListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new InterestResultListenerBehaviour(myAgent));

                    myAgent.addBehaviour(exchange);

                    isExchangeActive = true;
                    isExchangeTypeBeingSwitched = false;
                } else {
                    isExchangeActive = true;
                    isExchangeTypeBeingSwitched = true;

                    myAgent.addBehaviour(new InitiateExchangeListenerSCBehaviour(myAgent));
                    myAgent.removeBehaviour(this);
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return isExchangeActive;
        }

        @Override
        public void reset() {
            super.reset();

            isTradeOfferReceiver = false;

            // TODO
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
            return isAdPosted;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished advertising");
            }

            return 0;
        }
    }

    /* Exchange Requester Behaviours */

    public class ExchangeOpenListenerBehaviour extends Behaviour {
        private boolean didAdvertiseTimeSlots = false;
        public ExchangeOpenListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage exchangeOpenMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.CONFIRM);

            if (exchangeOpenMessage != null) {
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

                didAdvertiseTimeSlots = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return didAdvertiseTimeSlots;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished inquiring");
            }

            return 0;
        }
    }

    public class InterestResultListenerBehaviour extends Behaviour {
        private boolean resultReceived = false;

        public InterestResultListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage interestResultMessage = AgentHelper.receiveCFPReply(myAgent);

            if (interestResultMessage != null) {
                if (interestResultMessage.getPerformative() == ACLMessage.AGREE) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(interestResultMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer) {
                        boolean doesReceiverGainSocialCapita = completeRequestedExchange((TradeOffer) receivedObject);

                        // Adjust the agent's properties based on the trade offer
                        // The content of the incoming message is a boolean that carries the information whether
                        // the requesting party should lose social capita following the trade.
                        if (Boolean.parseBoolean(interestResultMessage.getConversationId())) {
                            totalSocialCapita--;
                        }

                        // Send a message to the Advertising agent to forward to the receiving Household agent
                        AgentHelper.sendMessage(
                                myAgent,
                                advertisingAgent,
                                Boolean.toString(doesReceiverGainSocialCapita),
                                ((TradeOffer) receivedObject).receiverAgent(),
                                ACLMessage.PROPAGATE
                        );
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be handled: the received object has an incorrect type or is null.");
                    }

                    numOfDailyAcceptedRequestedExchanges++;
                } else if (interestResultMessage.getPerformative() == ACLMessage.CANCEL) {
                    numOfDailyRejectedRequestedExchanges++;
                } else {
                    // TODO: what happens here when there are no results to the interest?
                    // TODO: we need to wait until a potential incoming trade request before we can skip straight to the end of the exchange
                    // TODO: this needs to be reflected with a class level flag
                }

                resultReceived = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return resultReceived;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished listening to the result of the interest");
            }

            myAgent.addBehaviour(new FinishExchangeRoundBehaviour(myAgent));

            return 0;
        }
    }

    /* Exchange Receiver Behaviours */

    public class TradeOfferListenerBehaviour extends Behaviour {
        private boolean proposalProcessed = false;
        public TradeOfferListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tradeOfferMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.PROPOSE);

            if (tradeOfferMessage != null) {
                if (!tradeOfferMessage.getConversationId().equals("No Expected Offers This Round")) {
                    int responsePerformative = ACLMessage.REJECT_PROPOSAL;
                    boolean doesRequesterLoseSocialCapita = false;

                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOfferMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer) {
                        // Consider the trade offer AND
                        // Check whether the requested time slot is actually owned by the agent
                        if (considerRequest((TradeOffer)receivedObject) && allocatedTimeSlots.contains(((TradeOffer)receivedObject).timeSlotRequested())) {
                            // Adjust the agent's properties based on the trade offer
                            doesRequesterLoseSocialCapita = completeReceivedExchange((TradeOffer)receivedObject);
                            responsePerformative = ACLMessage.ACCEPT_PROPOSAL;

                            myAgent.addBehaviour(new SocialCapitaSyncReceiverBehaviour(myAgent));
                        }

                        AgentHelper.sendMessage(
                                myAgent,
                                advertisingAgent,
                                Boolean.toString(doesRequesterLoseSocialCapita),
                                receivedObject,
                                responsePerformative
                        );
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be answered: the received object has an incorrect type or is null.");
                    }

                    isTradeOfferReceiver = true;
                } else {
                    // TODO: no offers expected but this agent can still be a requester
                }

                proposalProcessed = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return proposalProcessed;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished processing the proposal");
            }

            return 0;
        }
    }

    public class SocialCapitaSyncReceiverBehaviour extends Behaviour {
        private boolean socialCapitaSyncHandled = false;
        public SocialCapitaSyncReceiverBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.INFORM_IF);

            if (incomingSyncMessage != null) {
                if (!incomingSyncMessage.getConversationId().equals("No Syncing Necessary")) {
                    boolean doesReceiverAgentGainSocialCapita = Boolean.parseBoolean(incomingSyncMessage.getConversationId());

                    if (doesReceiverAgentGainSocialCapita) {
                        totalSocialCapita++;
                    }
                }

                socialCapitaSyncHandled = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return socialCapitaSyncHandled;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished syncing social capita");
            }

            return 0;
        }
    }

    public class InitiateExchangeListenerSCBehaviour extends Behaviour {
        private boolean isExchangeActive = false;
        private final SequentialBehaviour exchange = new SequentialBehaviour();

        public InitiateExchangeListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage newExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Initiated");

            if (newExchangeMessage != null || isExchangeTypeBeingSwitched) {
                if (config.getExchangeType() == ExchangeType.SmartContract) {
                    exchangeRoundStartTime = System.nanoTime();

                    if (config.isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "joining the exchange");
                    }

                    this.reset();

                    // Define the behaviours of the exchange
                    exchange.addSubBehaviour(new AdvertiseUnwantedTimeSlotsBehaviour(myAgent));
                    exchange.addSubBehaviour(new ExchangeOpenListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new InterestResultListenerSCBehaviour(myAgent));
                    exchange.addSubBehaviour(new TradeOfferListenerSCBehaviour(myAgent));

                    myAgent.addBehaviour(exchange);

                    isExchangeActive = true;
                    isExchangeTypeBeingSwitched = false;
                } else {
                    isExchangeActive = true;
                    isExchangeTypeBeingSwitched = true;

                    myAgent.addBehaviour(new InitiateExchangeListenerBehaviour(myAgent));
                    myAgent.removeBehaviour(this);
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return isExchangeActive;
        }

        @Override
        public void reset() {
            super.reset();

            isTradeStarted = false;
            isTradeOfferReceiver = false;
            // TODO
        }
    }

    public class InterestResultListenerSCBehaviour extends Behaviour {
        private boolean resultReceived = false;

        public InterestResultListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage interestResultMessage = AgentHelper.receiveCFPReply(myAgent);

            if (interestResultMessage != null && interestResultMessage.getSender().equals(advertisingAgent)) {
                if (interestResultMessage.getPerformative() == ACLMessage.AGREE) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(interestResultMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer) {
                        // Send the trade offer directly to the target agent
                        AgentHelper.sendMessage(
                                myAgent,
                                ((TradeOffer) receivedObject).receiverAgent(),
                                "New Offer",
                                receivedObject,
                                ACLMessage.PROPOSE
                        );

                        isTradeStarted = true;
                        myAgent.addBehaviour(new TradeOfferResponseListenerSCBehaviour(myAgent));
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be handled: the received object has an incorrect type or is null.");
                    }
                } else {
                    // TODO: get here if did not find any requested slots OR another agent already triggered the made interaction flag
                    // TODO: which means that this agent could still be receiving after this
                }

                resultReceived = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return resultReceived;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished listening to the result of the interest");
            }

            return 0;
        }
    }

    public class TradeOfferListenerSCBehaviour extends Behaviour {
        private boolean proposalProcessed = false;

        public TradeOfferListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tradeOfferMessage = AgentHelper.receiveMessage(myAgent, "New Offer", "No Expected Offers This Round");

            if (tradeOfferMessage != null) {
                if (!tradeOfferMessage.getConversationId().equals("No Expected Offers This Round")) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOfferMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer observableTradeOffer) {
                        // Consider the trade offer AND
                        // Check whether the requested time slot is actually owned by the agent
                        if (considerRequest(observableTradeOffer) && allocatedTimeSlots.contains(observableTradeOffer.timeSlotRequested())) {
                            // Fire the property change in the trade offer object and thus notify its observer,
                            // the smart contract
                            SmartContract.getInstance().triggerSmartContract((HouseholdAgent) myAgent, observableTradeOffer);
                        } else {
                            AgentHelper.sendMessage(
                                    myAgent,
                                    tradeOfferMessage.getSender(),
                                    "Trade Offer Response",
                                    observableTradeOffer,
                                    ACLMessage.REJECT_PROPOSAL
                            );
                        }

                        // Finish the current round of exchange after considering the trade
                        myAgent.addBehaviour(new FinishExchangeRoundBehaviour(myAgent));
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be answered: the received object has an incorrect type or is null.");
                    }

                    isTradeOfferReceiver = true;
                } else {
                    // This happens when the agent receives no trade requests but does send a request
                    if (!isTradeStarted) {
                        myAgent.addBehaviour(new FinishExchangeRoundBehaviour(myAgent));
                    }
                }

                proposalProcessed = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return proposalProcessed;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished processing the proposal");
            }

            return 0;
        }
    }

    public class TradeOfferResponseListenerSCBehaviour extends Behaviour {
        private boolean proposalReplyReceived = false;

        public TradeOfferResponseListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage proposalReplyMessage = AgentHelper.receiveProposalReply(myAgent);

            if (proposalReplyMessage != null) {
                Serializable processedTradeOfferObject = "Rejected";

                if (proposalReplyMessage.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(proposalReplyMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer processedTradeOffer) {
                        boolean doesReceiverGainSocialCapita = completeRequestedExchange(processedTradeOffer);

                        // Adjust the agent's properties based on the trade offer
                        // The content of the incoming message is a boolean that carries the information whether
                        // the requesting party should lose social capita following the trade.
                        if (Boolean.parseBoolean(proposalReplyMessage.getConversationId())) {
                            totalSocialCapita--;
                        }

                        AgentHelper.sendMessage(
                                myAgent,
                                processedTradeOffer.receiverAgent(),
                                Boolean.toString(doesReceiverGainSocialCapita),
                                processedTradeOffer,
                                ACLMessage.INFORM_IF
                        );

                        processedTradeOfferObject = processedTradeOffer;
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Accepted trade offer cannot be processed: the received object has an incorrect type or is null.");
                    }

                    numOfDailyAcceptedRequestedExchanges++;
                } else if (proposalReplyMessage.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    numOfDailyRejectedRequestedExchanges++;
                }

                AgentHelper.sendMessage(
                        myAgent,
                        advertisingAgent,
                        "Trade Outcome",
                        processedTradeOfferObject,
                        ACLMessage.INFORM
                );

                myAgent.addBehaviour(new FinishExchangeRoundBehaviour(myAgent));

                proposalReplyReceived = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return proposalReplyReceived;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished listening to the trade offer reply");
            }

            return 0;
        }
    }

    public class FinishExchangeRoundBehaviour extends OneShotBehaviour {
        public FinishExchangeRoundBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "household finished");
            }

            currentSatisfaction = AgentHelper.calculateSatisfaction(allocatedTimeSlots, requestedTimeSlots);
            long exchangeRoundEndTime = System.nanoTime();

            AgentHelper.sendMessage(
                    myAgent,
                    advertisingAgent,
                    "Exchange Done",
                    new EndOfExchangeHouseholdDataHolder(
                            currentSatisfaction,
                            isTradeOfferReceiver,
                            exchangeRoundEndTime - exchangeRoundStartTime
                    ),
                    ACLMessage.INFORM
            );

            switch (config.getExchangeType()){
                case MessagePassing -> myAgent.addBehaviour(new InitiateExchangeListenerBehaviour(myAgent));
                case SmartContract -> myAgent.addBehaviour(new InitiateExchangeListenerSCBehaviour(myAgent));
            }
        }
    }

    public class SocialLearningListenerBehaviour extends Behaviour {
        private boolean processedSocialLearningMessage = false;

        public SocialLearningListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage socialLearningMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, ACLMessage.QUERY_IF);

            if (socialLearningMessage != null) {
                if (socialLearningMessage.getConversationId().equals("Selected for Social Learning")) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(socialLearningMessage, myAgent.getLocalName(), AgentContact.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof AgentContact advertisingAgentsHouseholdContact) {
                        // TODO: Cite Arena code
                        // Copy the observed agents strategy if it is better than its own, with likelihood dependent on the
                        // difference between the agents satisfaction and the observed satisfaction.
                        double observedAgentSatisfaction = advertisingAgentsHouseholdContact.getCurrentSatisfaction();

                        if (Math.round(currentSatisfaction * config.getNumOfSlotsPerAgent()) < Math.round(observedAgentSatisfaction * config.getNumOfSlotsPerAgent())) {
                            double difference = observedAgentSatisfaction - currentSatisfaction;

                            if (difference >= 0) {
                                double learningChance = 1 / (1 + (Math.exp(-config.getBeta() * difference)));
                                double normalisedLearningChance = (learningChance * 2) - 1;

                                double threshold = config.getRandom().nextDouble();

                                if (normalisedLearningChance > threshold) {
                                    agentType = advertisingAgentsHouseholdContact.getType();
                                }
                            }
                        }
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Social Learning cannot be started: the received object has an incorrect type or is null.");
                    }
                }

                processedSocialLearningMessage = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return processedSocialLearningMessage;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished with social learning");
            }

            AgentHelper.sendMessage(
                    myAgent,
                    advertisingAgent,
                    "Social Learning Done",
                    new AgentContact(myAgent.getAID(), agentType, currentSatisfaction),
                    ACLMessage.INFORM
            );

            myAgent.addBehaviour(new CallItADayBehaviour(myAgent));

            return 0;
        }
    }

    public class CallItADayBehaviour extends OneShotBehaviour {
        public CallItADayBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            AgentHelper.sendMessage(
                    myAgent,
                    advertisingAgent,
                    "Done",
                    new EndOfDayHouseholdAgentDataHolder(
                            numOfDailyRejectedReceivedExchanges,
                            numOfDailyRejectedRequestedExchanges,
                            numOfDailyAcceptedRequestedExchanges,
                            numOfDailyAcceptedReceivedExchangesWithSocialCapita,
                            numOfDailyAcceptedReceivedExchangesWithoutSocialCapita,
                            totalSocialCapita
                    ),
                    ACLMessage.INFORM
            );
        }
    }

    private void initialAgentSetup() {
        if (!this.areHouseholdsFound && config.getExchangeType() == ExchangeType.SmartContract) {
            addBehaviour(new FindHouseholdsBehaviour(this));
        }

        this.agentType = AgentHelper.determineAgentType(this.getLocalName());

        // Initialise local attributes
        this.requestedTimeSlots = new ArrayList<>();
        this.allocatedTimeSlots = new ArrayList<>();
        this.timeSlotSatisfactionPairs = new ArrayList<>();
        this.favours = new HashMap<>();
        this.numOfDailyRejectedReceivedExchanges = 0;
        this.numOfDailyRejectedRequestedExchanges = 0;
        this.numOfDailyAcceptedRequestedExchanges = 0;
        this.totalSocialCapita = 0;
        this.numOfDailyAcceptedReceivedExchangesWithSocialCapita = 0;
        this.numOfDailyAcceptedReceivedExchangesWithoutSocialCapita = 0;
        initializeFavoursStore();
    }

    // TODO: Cite Arena code
    /**
     * Identifies all other Agents in the ExchangeArena and initialises counts of favours given to and received from
     * each other Agent.
     */
    private void initializeFavoursStore() {
        if (config.doesUtiliseSocialCapita()) {
            if (!this.favours.isEmpty()) {
                this.favours.clear();
            }

            for (int i = 1; i <= config.getPopulationCount(); i++) {
                // Prevent the household agent from being registered in the favours storage
                if (!this.getLocalName().equals("Household-" + i)) {
                    // Initially, no favours are owed or have been given to any other agent.
                    // The key is the other agent's nickname (or local name).
                    this.favours.put("Household-" + i, 0);
                }
            }
        }
    }

    // TODO: Cite Arena code
    /**
     * Determine whether the Agent will be willing to accept a received exchange request.
     *
     * @param offer The exchange that is to be considered.
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
                    this.numOfDailyAcceptedReceivedExchangesWithoutSocialCapita++;
                } else if (Double.compare(potentialSatisfaction, currentSatisfaction) == 0) {
                    if (config.doesUtiliseSocialCapita()) {
                        if (favours.get(offer.requesterAgent().getLocalName()) < 0) {
                            exchangeRequestApproved = true;
                            this.numOfDailyAcceptedReceivedExchangesWithSocialCapita++;
                        }
                    } else {
                        // When social capital isn't used, social agents always accept neutral exchanges.
                        exchangeRequestApproved = true;
                        this.numOfDailyAcceptedReceivedExchangesWithoutSocialCapita++;
                    }
                }
            } else {
                // Selfish Agents and Agents with no known type use the default selfish approach.
                // Selfish Agents only accept offers that improve their individual satisfaction.
                if (Double.compare(potentialSatisfaction, currentSatisfaction) > 0) {
                    exchangeRequestApproved = true;
                    this.numOfDailyAcceptedReceivedExchangesWithoutSocialCapita++;
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
    public boolean completeReceivedExchange(TradeOffer offer) {
        boolean otherAgentSCLoss = false;

        double previousSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Update the Agents allocated time-slots.
        this.allocatedTimeSlots.remove(offer.timeSlotRequested());
        this.allocatedTimeSlots.add(offer.timeSlotOffered());

        double newSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);

        // Update the Agents relationship with the other Agent involved in the exchange.
        if (config.doesUtiliseSocialCapita()) {
            if (Double.compare(newSatisfaction, previousSatisfaction) <= 0 && this.agentType == AgentStrategyType.SOCIAL) {
                int currentNumberOfFavours = this.favours.get(offer.requesterAgent().getLocalName());

                this.favours.replace(offer.requesterAgent().getLocalName(), currentNumberOfFavours + 1);

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
    private boolean completeRequestedExchange(TradeOffer offer) {
        boolean otherAgentSCGain = false;

        double previousSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Update the Agents allocated time-slots.
        this.allocatedTimeSlots.remove(offer.timeSlotOffered());
        this.allocatedTimeSlots.add(offer.timeSlotRequested());

        double newSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);

        // Update the Agents relationship with the other Agent involved in the exchange.
        if (config.doesUtiliseSocialCapita()) {
            if (Double.compare(newSatisfaction, previousSatisfaction) > 0 && this.agentType == AgentStrategyType.SOCIAL) {
                int currentNumberOfFavours = this.favours.get(offer.receiverAgent().getLocalName());

                this.favours.replace(offer.receiverAgent().getLocalName(), currentNumberOfFavours - 1);

                otherAgentSCGain = true;
            }
        }

        this.numOfDailyAcceptedRequestedExchanges++;

        return otherAgentSCGain;
    }

    public void incrementTotalSocialCapita() {
        this.totalSocialCapita++;
    }
}
