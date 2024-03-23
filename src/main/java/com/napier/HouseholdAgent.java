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
import java.util.*; // TODO: get rid of the wildcard import

public class HouseholdAgent extends Agent {
    // Agent arguments
    private AgentStrategyType agentType;
    private ArrayList<TimeSlot> requestedTimeSlots;
    private ArrayList<TimeSlot> allocatedTimeSlots;
    private ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs;
    private HashMap<String, Integer> favours;
    private boolean isExchangeRequestApproved;
    private int totalSocialCapital; // TODO: rename these to SocialCapita
    private int numOfDailyExchangesWithSocialCapital;
    private int numOfDailyExchangesWithoutSocialCapital;
    private int numOfDailyRejectedReceivedExchanges;
    private int numOfDailyRejectedRequestedExchanges;
    private int numOfDailyAcceptedRequestedExchanges;
    private double[] dailyDemandCurve;
    private double dailyDemandValue;
    private String currentExchangeTradeOfferString;

    // Agent contact attributes
    private AID tickerAgent;
    private AID advertisingAgent;
    private ArrayList<AgentContact> householdAgentContacts;

    @Override
    protected void setup() {
        initialAgentSetup();

        AgentHelper.registerAgent(this, "Household");

        if (RunConfigurationSingleton.getInstance().getExchangeType() == ExchangeType.SmartContract) {
            addBehaviour(new FindHouseholdsBehaviour(this));
        }

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
            // Populate the contact collections
            householdAgentContacts = AgentHelper.saveAgentContacts(myAgent, "Household");
        }
    }

    public class TickerDailyBehaviour extends CyclicBehaviour {
        public TickerDailyBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tick = AgentHelper.receiveMessage(myAgent, tickerAgent, ACLMessage.INFORM);

            if (tick != null && tickerAgent != null) {
                // Set up the daily tasks
                if (!tick.getConversationId().equals("Terminate")) {
                    // Do a reset on all agent attributes on each new simulation run
                    if (tick.getConversationId().equals("New Run")) {
                        initialAgentSetup();

                        if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                            AgentHelper.printAgentLog(myAgent.getLocalName(), "Reset for new run");
                        }
                    }

                    // Set the daily tracking data to their initial values at the start of the day
                    reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new DetermineDailyDemandBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DetermineTimeSlotPreferenceBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new CalculateSlotSatisfactionBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new ReceiveRandomInitialTimeSlotAllocationBehaviour(myAgent));

                    switch (RunConfigurationSingleton.getInstance().getExchangeType()){
                        case MessagePassing -> dailyTasks.addSubBehaviour(new InitiateExchangeListenerBehaviour(myAgent));
                        case SmartContract -> dailyTasks.addSubBehaviour(new InitiateExchangeListenerSCBehaviour(myAgent));
                    }

                    myAgent.addBehaviour(dailyTasks);
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
            ACLMessage incomingAllocationMessage = AgentHelper.receiveMessage(myAgent, advertisingAgent, "Initial Allocation Enclosed.", ACLMessage.INFORM);

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

    public class InitiateExchangeListenerBehaviour extends Behaviour {
        private boolean isExchangeActive = false;
        private final SequentialBehaviour exchange = new SequentialBehaviour();
        public InitiateExchangeListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (!isExchangeActive) {
                ACLMessage newExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Initiated");

                if (newExchangeMessage != null) {
                    if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "joining the exchange");
                    }

                    reset();

                    // Define the behaviours of the exchange
                    exchange.addSubBehaviour(new AdvertiseUnwantedTimeSlotsBehaviour(myAgent));
                    exchange.addSubBehaviour(new ExchangeOpenListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new TradeOfferListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new InterestResultListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new SocialCapitaSyncReceiverBehaviour(myAgent));

                    myAgent.addBehaviour(exchange);

                    isExchangeActive = true;
                } else {
                    block();
                }
            }
        }

        @Override
        public boolean done() {
            return exchange.done();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "household finished");
            }

            AgentHelper.sendMessage(
                    myAgent,
                    advertisingAgent,
                    "Exchange Done",
                    new AgentContact(
                            myAgent.getAID(),
                            agentType,
                            AgentHelper.calculateSatisfaction(allocatedTimeSlots, requestedTimeSlots)
                    ),
                    ACLMessage.INFORM
            );

            myAgent.addBehaviour(new InitiateExchangeListenerBehaviour(myAgent));

            return 0;
        }

        @Override
        public void reset() {
            super.reset();

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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
                if (!didAdvertiseTimeSlots) {
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
                }
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
                            boolean doesReceiverGainSocialCapita = completeRequestedExchange((TradeOffer) incomingObject);

                            // Adjust the agent's properties based on the trade offer
                            // The content of the incoming message is a boolean that carries the information whether
                            // the requesting party should lose social capita following the trade.
                            if (Boolean.parseBoolean(interestResultMessage.getConversationId())) {
                                totalSocialCapital--;
                            }

                            // Send a message to the Advertising agent to forward to the receiving Household agent
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished listening to the result of the interest");
            }

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
                } else {
                    AgentHelper.sendMessage(
                            myAgent,
                            advertisingAgent,
                            "No Offers Reply",
                            ACLMessage.REFUSE
                    );
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
                        totalSocialCapital++;
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
            if (!isExchangeActive) {
                ACLMessage newExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Initiated");

                if (newExchangeMessage != null) {
                    if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                        AgentHelper.printAgentLog(myAgent.getLocalName(), "joining the exchange");
                    }

                    reset();

                    // Define the behaviours of the exchange
                    exchange.addSubBehaviour(new AdvertiseUnwantedTimeSlotsBehaviour(myAgent));
                    exchange.addSubBehaviour(new ExchangeOpenListenerBehaviour(myAgent));
                    exchange.addSubBehaviour(new InterestResultListenerSCBehaviour(myAgent));
                    exchange.addSubBehaviour(new TradeOfferListenerSCBehaviour(myAgent));

                    myAgent.addBehaviour(exchange);

                    isExchangeActive = true;
                } else {
                    block();
                }
            }
        }

        @Override
        public boolean done() {
            return exchange.done();
        }

        @Override
        public void reset() {
            super.reset();

            currentExchangeTradeOfferString = "";
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
                    Serializable incomingObject = null;

                    try {
                        incomingObject = interestResultMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming interest result trade offer is unreadable: " + e.getMessage());
                    }

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof TradeOffer) {
                            // Send the trade offer directly to the target agent
                            AgentHelper.sendMessage(
                                    myAgent,
                                    ((TradeOffer) incomingObject).receiverAgent(),
                                    "New Offer",
                                    incomingObject,
                                    ACLMessage.PROPOSE
                            );

                            myAgent.addBehaviour(new TradeOfferResponseListenerSCBehaviour(myAgent));
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be handled: the received object has an incorrect type.");
                        }
                    }
                } else if (interestResultMessage.getPerformative() == ACLMessage.CANCEL) {
                    numOfDailyRejectedRequestedExchanges++; // TODO: this cannot occur here, find a way so it can
                } else {
                    myAgent.addBehaviour(new FinishSCExchangeRoundBehaviour(myAgent));
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
            ACLMessage tradeOfferMessage = AgentHelper.receiveMessage(myAgent, "New Offer");

            if (tradeOfferMessage != null) {
                if (!tradeOfferMessage.getConversationId().equals("No Expected Offers This Round")) {
                    // Make sure the incoming object is readable
                    Serializable incomingObject = null;

                    try {
                        incomingObject = tradeOfferMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming trade offer is unreadable: " + e.getMessage());
                    }

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof TradeOffer observableTradeOffer) {
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
                                        ACLMessage.REJECT_PROPOSAL
                                );

                                myAgent.addBehaviour(new FinishSCExchangeRoundBehaviour(myAgent));
                            }
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer cannot be answered: the received object has an incorrect type.");
                        }
                    }
                } else {
                    System.out.println("notified");
                    myAgent.addBehaviour(new FinishSCExchangeRoundBehaviour(myAgent));
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
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
                if (proposalReplyMessage.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    System.out.println("performative: " + proposalReplyMessage.getPerformative());
                    System.out.println("sent by: " + proposalReplyMessage.getSender().getLocalName() + " to: " + myAgent.getLocalName());
                    // Make sure the incoming object is readable
                    Serializable incomingObject = null;

                    try {
                        incomingObject = proposalReplyMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming accepted trade offer is unreadable: " + e.getMessage());
                    }

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof TradeOffer acceptedTradeOffer) {
                            boolean doesReceiverGainSocialCapita = completeRequestedExchange(acceptedTradeOffer);

                            // Adjust the agent's properties based on the trade offer
                            // The content of the incoming message is a boolean that carries the information whether
                            // the requesting party should lose social capita following the trade.
                            if (Boolean.parseBoolean(proposalReplyMessage.getConversationId())) {
                                totalSocialCapital--;
                            }

                            AgentHelper.sendMessage(
                                    myAgent,
                                    acceptedTradeOffer.receiverAgent(),
                                    Boolean.toString(doesReceiverGainSocialCapita),
                                    ACLMessage.INFORM_IF
                            );
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Accepted trade offer cannot be processed: the received object has an incorrect type.");
                        }
                    }
                }

                myAgent.addBehaviour(new FinishSCExchangeRoundBehaviour(myAgent));

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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished listening to the trade offer reply");
            }

            return 0;
        }
    }

    public class FinishSCExchangeRoundBehaviour extends OneShotBehaviour {
        public FinishSCExchangeRoundBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "household finished");
            }

            AgentHelper.sendMessage(
                    myAgent,
                    advertisingAgent,
                    "Exchange Done",
                    new AgentContact(
                            myAgent.getAID(),
                            agentType,
                            AgentHelper.calculateSatisfaction(allocatedTimeSlots, requestedTimeSlots)
                    ),
                    ACLMessage.INFORM
            );

            myAgent.addBehaviour(new InitiateExchangeListenerSCBehaviour(myAgent));
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
                double learningCurrentSatisfaction = AgentHelper.calculateSatisfaction(allocatedTimeSlots, requestedTimeSlots);

                if (socialLearningMessage.getConversationId().equals("Selected for Social Learning")) {
                    // Make sure the incoming object is readable
                    Serializable incomingObject = null;

                    try {
                        incomingObject = socialLearningMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming agent contact is unreadable: " + e.getMessage());
                    }

                    double learningAgentSatisfaction = AgentHelper.calculateSatisfaction(allocatedTimeSlots, requestedTimeSlots);

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof AgentContact) {
                            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

                            // TODO: Cite Arena code
                            // Copy the observed agents strategy if it is better than its own, with likelihood dependent on the
                            // difference between the agents satisfaction and the observed satisfaction.
                            double observedAgentSatisfaction = ((AgentContact)incomingObject).getCurrentSatisfaction();

                            if (Math.round(learningAgentSatisfaction * config.getNumOfSlotsPerAgent()) < Math.round(observedAgentSatisfaction * config.getNumOfSlotsPerAgent())) {
                                double difference = observedAgentSatisfaction - learningAgentSatisfaction;

                                if (difference >= 0) {
                                    double learningChance = 1 / (1 + (Math.exp(-config.getBeta() * difference)));
                                    double normalisedLearningChance = (learningChance * 2) - 1;

                                    double threshold = config.getRandom().nextDouble();

                                    if (normalisedLearningChance > threshold) {
                                        agentType = ((AgentContact)incomingObject).getType();
                                    }
                                }
                            }
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Social Learning cannot be started: the received object has an incorrect type.");
                        }
                    }
                }

                processedSocialLearningMessage = true;

                AgentHelper.sendMessage(
                        myAgent,
                        advertisingAgent,
                        "Social Learning Done",
                        new AgentContact(myAgent.getAID(), agentType, learningCurrentSatisfaction),
                        ACLMessage.INFORM
                );
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
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "finished with social learning");
            }

            return 0;
        }
    }

    private void initialAgentSetup() {
        // Import the arguments
        this.agentType = (AgentStrategyType)getArguments()[0];

        // Initialise local attributes
        this.requestedTimeSlots = new ArrayList<>();
        this.allocatedTimeSlots = new ArrayList<>();
        this.timeSlotSatisfactionPairs = new ArrayList<>();
        this.favours = new HashMap<>();
        this.totalSocialCapital = 0;
        this.numOfDailyExchangesWithSocialCapital = 0;
        this.numOfDailyExchangesWithoutSocialCapital = 0;
        this.numOfDailyRejectedReceivedExchanges = 0;
        this.numOfDailyRejectedRequestedExchanges = 0;
        this.numOfDailyAcceptedRequestedExchanges = 0;
        initializeFavoursStore();
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
                    this.numOfDailyExchangesWithoutSocialCapital++;
                } else if (Double.compare(potentialSatisfaction, currentSatisfaction) == 0) {
                    if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
                        if (favours.get(offer.requesterAgent().getLocalName()) < 0) {
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
    public boolean completeReceivedExchange(TradeOffer offer) {
        boolean otherAgentSCLoss = false;

        double previousSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);
        // Update the Agents allocated time-slots.
        this.allocatedTimeSlots.remove(offer.timeSlotRequested());
        this.allocatedTimeSlots.add(offer.timeSlotOffered());

        double newSatisfaction = AgentHelper.calculateSatisfaction(this.allocatedTimeSlots, this.requestedTimeSlots);

        // Update the Agents relationship with the other Agent involved in the exchange.
        if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
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
        if (RunConfigurationSingleton.getInstance().doesUtiliseSocialCapital()) {
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
        this.totalSocialCapital++;
    }
}
