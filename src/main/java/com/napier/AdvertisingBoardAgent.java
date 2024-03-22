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

public class AdvertisingBoardAgent extends Agent {
    private ArrayList<TimeSlot> availableTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> adverts;
    private int numOfTradesStarted;
    private int numOfSuccessfulExchanges;
    private ArrayList<AID> agentsToNotify;
    private int exchangeTimeout;

    // Agent contact attributes
    private AID tickerAgent;
    private ArrayList<AgentContact> householdAgentContacts;
    private HashMap<AID, Boolean> householdAgentsInteractions;

    @Override
    protected void setup() {
        initialAgentSetup();

        AgentHelper.registerAgent(this, "Advertising-board");

        addBehaviour(new FindTickerBehaviour(this));
        addBehaviour(new FindHouseholdsBehaviour(this));
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
                if (!tick.getConversationId().equals("Terminate")) {
                    // Do a reset on all agent attributes on each new simulation run
                    if (tick.getConversationId().equals("New Run")) {
                        initialAgentSetup();

                        if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                            AgentHelper.printAgentLog(myAgent.getLocalName(), "Reset for new run");
                        }

                        myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                    }

                    // Set the daily resources and adverts to their initial values
                    reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new GenerateTimeSlotsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DistributeInitialRandomTimeSlotAllocations(myAgent));

                    switch (RunConfigurationSingleton.getInstance().getExchangeType()) {
                        case MessagePassing -> dailyTasks.addSubBehaviour(new InitiateExchangeBehaviour(myAgent));
                        case SmartContract -> dailyTasks.addSubBehaviour(new InitiateSCExchangeBehaviour(myAgent));
                    }

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
            availableTimeSlots.clear();
            adverts.clear();
            exchangeTimeout = 0;
        }
    }

    public class GenerateTimeSlotsBehaviour extends OneShotBehaviour {
        public GenerateTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

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

            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "Time Slots generated: " + availableTimeSlots.size());
            }
        }
    }

    public class DistributeInitialRandomTimeSlotAllocations extends OneShotBehaviour {
        public DistributeInitialRandomTimeSlotAllocations(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            Collections.shuffle(householdAgentContacts, config.getRandom());

            for (AgentContact contact : householdAgentContacts) {
                // TODO: Cite Arena code
                TimeSlot[] initialTimeSlots = new TimeSlot[config.getNumOfSlotsPerAgent()];

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
                        contact.getAgentIdentifier(),
                        "Initial Allocation Enclosed.",
                        new SerializableTimeSlotArray(initialTimeSlots),
                        ACLMessage.INFORM
                );
            }
        }
    }

    public class InitiateExchangeBehaviour extends OneShotBehaviour {
        private final SequentialBehaviour exchange = new SequentialBehaviour();

        public InitiateExchangeBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            reset();

            exchange.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new InterestListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new TradeOfferResponseListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new SocialCapitaSyncPropagateBehaviour(myAgent));
            exchange.addSubBehaviour(new ExchangeRoundOverListener(myAgent));

            myAgent.addBehaviour(exchange);

            // Broadcast the start of the exchange round to all household agents
            AgentHelper.sendMessage(
                    myAgent,
                    getHouseholdAgentAIDList(),
                    "Exchange Initiated",
                    ACLMessage.REQUEST
            );
        }

        @Override
        public void reset() {
            super.reset();

            exchangeValuesReset();
        }
    }

    public class NewAdvertListenerBehaviour extends Behaviour {
        private int numOfAdvertsReceived = 0;

        public NewAdvertListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage advertisingMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.REQUEST);

            if (advertisingMessage != null) {
                Serializable incomingObject = null;

                try {
                    incomingObject = advertisingMessage.getContentObject();
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming new advert is unreadable: " + e.getMessage());
                }

                if (incomingObject != null) {
                    // Make sure the incoming object is of the expected type and the advert is not empty
                    if (incomingObject instanceof SerializableTimeSlotArray) {
                        // Register (or update) the advert
                        adverts.put(
                                advertisingMessage.getSender(),
                                new ArrayList<>(Arrays.asList(((SerializableTimeSlotArray)incomingObject).timeSlots()))
                        );

                        numOfAdvertsReceived++;
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Advert cannot be registered: the received object has an incorrect type.");
                    }
                }

                // This has to be integrated in this behaviour to make sure that the adverts have been collected
                if (numOfAdvertsReceived == RunConfigurationSingleton.getInstance().getPopulationCount() && householdAgentContacts.size() == numOfAdvertsReceived) {
                    // Shuffle the agent contact list before broadcasting the exchange open message
                    // This likely determines the order in which agents participate in the exchange
                    Collections.shuffle(householdAgentContacts, RunConfigurationSingleton.getInstance().getRandom());

                    // Broadcast to all agents that the exchange is open
                    AgentHelper.sendMessage(
                            myAgent,
                            getHouseholdAgentAIDList(),
                            "Exchange is Open",
                            ACLMessage.CONFIRM
                    );
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return numOfAdvertsReceived == RunConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for new adverts");
            }

            return 0;
        }
    }

    public class InterestListenerBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceiveTradeOffer = new ArrayList<>();

        public InterestListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
            boolean refuseRequest = true;

            ACLMessage interestMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (interestMessage != null && adverts.size() == config.getPopulationCount()) {
                // Check if the household agent has made interaction with another household agent in the current exchange round
                if (!householdAgentsInteractions.get(interestMessage.getSender())) {
                    // Flip the "made interaction" flag
                    householdAgentsInteractions.replace(interestMessage.getSender(), true);

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> sendersAdvertisedTimeSlots = adverts.get(interestMessage.getSender());

                    // Find out if the sender has any timeslots available to trade
                    if (!sendersAdvertisedTimeSlots.isEmpty()) {
                        // Make sure the incoming object is readable
                        Serializable incomingObject = null;

                        try {
                            incomingObject = interestMessage.getContentObject();
                        } catch (UnreadableException e) {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming message about an interest in timeslots is unreadable: " + e.getMessage());
                        }

                        if (incomingObject != null) {
                            // Make sure the incoming object is of the expected type and the advert is not empty
                            if (incomingObject instanceof SerializableTimeSlotArray) {
                                TimeSlot[] desiredTimeSlots = ((SerializableTimeSlotArray) incomingObject).timeSlots();
                                TimeSlot targetTimeSlot = null;
                                AID targetOwner = null;

                                // TODO: Cite Arena code
                                ArrayList<AID> shuffledAdvertPosters = new ArrayList<>(adverts.keySet());

                                // Remove the requesting agent from the temp advert catalogue to avoid an unnecessary check
                                shuffledAdvertPosters.remove(interestMessage.getSender());
                                Collections.shuffle(shuffledAdvertPosters, config.getRandom());

                                // Find the desired timeslot in the published adverts
                                browsingTimeSlots:
                                for (TimeSlot desiredTimeSlot : desiredTimeSlots) {
                                    for (AID advertPoster : shuffledAdvertPosters) {
                                        // Check if the potential receiving household agent has made an interaction in the current exchange round
                                        if (!householdAgentsInteractions.get(advertPoster)) {
                                            ArrayList<TimeSlot> timeSlotsForTrade = adverts.get(advertPoster);

                                            for (TimeSlot timeSlotForTrade : timeSlotsForTrade) {
                                                if (desiredTimeSlot.equals(timeSlotForTrade)) {
                                                    targetTimeSlot = timeSlotForTrade;
                                                    targetOwner = advertPoster;

                                                    // Add the target agent to the list of agents to receive a trade offer
                                                    agentsToReceiveTradeOffer.add(advertPoster);
                                                    // Flip the target agent's "made interaction" flag to true so that
                                                    // it does not get paired up with other agents this round
                                                    householdAgentsInteractions.replace(advertPoster, true);

                                                    break browsingTimeSlots;
                                                }
                                            }
                                        } else {
                                            // TODO: let the requesting agent know that the receiving agent is already occupied this round
                                        }
                                    }
                                }

                                // Check if the sender has any timeslots to offer in return and if a desired timeslot was found
                                if (targetTimeSlot != null) {
                                    // Offer the sender's least wanted timeslot - the first element of the advert
                                    // Send the trade offer to the agent that has the desired timeslot, with the
                                    // sender's nickname as the text content
                                    AgentHelper.sendMessage(
                                            myAgent,
                                            targetOwner,
                                            "New Offer",
                                            new TradeOffer(
                                                    interestMessage.getSender(),
                                                    targetOwner,
                                                    sendersAdvertisedTimeSlots.getFirst(),
                                                    targetTimeSlot
                                            ),
                                            ACLMessage.PROPOSE
                                    );

                                    numOfTradesStarted++;
                                    refuseRequest = false;
                                }
                            } else {
                                AgentHelper.printAgentError(myAgent.getLocalName(), "Interest for timeslots cannot be processed: the received object has an incorrect type.");
                            }
                        }
                    }
                } else {
                    // TODO: let the requesting agent know that it has already made interaction therefore cannot make any more requests
                }

                numOfRequestsProcessed++;

                // After processing each CFP, check if all agents have sent this message
                final int populationCount = RunConfigurationSingleton.getInstance().getPopulationCount();

                if (numOfRequestsProcessed == populationCount && agentsToReceiveTradeOffer.size() <= populationCount) {
                    // By subtracting the arraylist of agents from the list of all agents, get the agents who did not
                    // receive a trade request in the current exchange round and notify them.
                    agentsToNotify = new ArrayList<>(getHouseholdAgentAIDList());
                    agentsToNotify.removeAll(agentsToReceiveTradeOffer);

                    // Broadcast the "no offers" message to the agents who did not receive a trade offer for various reasons
                    AgentHelper.sendMessage(
                            myAgent,
                            agentsToNotify,
                            "No Expected Offers This Round",
                            ACLMessage.PROPOSE
                    );
                }

                if (refuseRequest) {
                    // Reach this part if any of these events happened:
                    // - the sender had no timeslots advertised to offer in return
                    // - the object sent by the requester can't be processed
                    // - the recipient of the request has already made interaction with another agent this round
                    // - none of the desired timeslots were found in the adverts
                    AgentHelper.sendMessage(
                            myAgent,
                            interestMessage.getSender(),
                            "Desired Timeslots Not Available",
                            ACLMessage.REFUSE
                    );
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return numOfRequestsProcessed == RunConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return 0;
        }
    }

    public class TradeOfferResponseListenerBehaviour extends Behaviour {
        private int numOfTradeOfferReplies = 0;

        public TradeOfferResponseListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage tradeOfferResponseMessage = AgentHelper.receiveProposalReply(myAgent);

            if (tradeOfferResponseMessage != null) {
                if (tradeOfferResponseMessage.getPerformative() != ACLMessage.REFUSE) {
                    // Make sure the incoming object is readable
                    Serializable incomingObject = null;

                    try {
                        incomingObject = tradeOfferResponseMessage.getContentObject();
                    } catch (UnreadableException e) {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer response message is unreadable: " + e.getMessage());
                    }

                    if (incomingObject != null) {
                        // Make sure the incoming object is of the expected type
                        if (incomingObject instanceof TradeOffer) {
                            if (tradeOfferResponseMessage.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                                // Handle the accepted trade offer
                                // Remove the traded timeslots from the adverts
                                adverts.get(tradeOfferResponseMessage.getSender()).remove(((TradeOffer) incomingObject).timeSlotRequested());
                                adverts.get(((TradeOffer) incomingObject).senderAgent()).remove(((TradeOffer) incomingObject).timeSlotOffered());

                                // Notify the agent who initiated the interest
                                AgentHelper.sendMessage(
                                        myAgent,
                                        ((TradeOffer) incomingObject).senderAgent(),
                                        tradeOfferResponseMessage.getConversationId(),
                                        incomingObject,
                                        ACLMessage.AGREE
                                );

                                numOfSuccessfulExchanges++;
                            } else {
                                AgentHelper.sendMessage(
                                        myAgent,
                                        ((TradeOffer) incomingObject).senderAgent(),
                                        "Trade Rejected",
                                        ACLMessage.CANCEL
                                );

                                agentsToNotify.add(tradeOfferResponseMessage.getSender());
                            }
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer response cannot be acted upon: the received object has an incorrect type.");
                        }
                    }

                    numOfTradeOfferReplies++;
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return numOfTradesStarted == numOfTradeOfferReplies;
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for trades");
            }

            return 0;
        }
    }

    public class SocialCapitaSyncPropagateBehaviour extends Behaviour {
        private int numOfMessagesPropagated = 0;
        private boolean allSyncActionsHandled = false;

        public SocialCapitaSyncPropagateBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (numOfSuccessfulExchanges > 0) {
                ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.PROPAGATE);

                if (incomingSyncMessage != null) {
                    if (!incomingSyncMessage.getConversationId().equals("No Syncing Necessary")) {
                        // Make sure the incoming object is readable
                        Serializable incomingObject = null;

                        try {
                            incomingObject = incomingSyncMessage.getContentObject();
                        } catch (UnreadableException e) {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer receiver AID is unreadable: " + e.getMessage());
                        }

                        if (incomingObject != null) {
                            // Make sure the incoming object is of the expected type
                            if (incomingObject instanceof AID) {
                                AgentHelper.sendMessage(
                                        myAgent,
                                        (AID) incomingObject,
                                        incomingSyncMessage.getConversationId(),
                                        ACLMessage.INFORM_IF
                                );
                            }
                        }
                    }

                    numOfMessagesPropagated++;

                    if (numOfMessagesPropagated == numOfSuccessfulExchanges) {
                        AgentHelper.sendMessage(
                                myAgent,
                                agentsToNotify,
                                "No Syncing Necessary",
                                ACLMessage.INFORM_IF
                        );

                        allSyncActionsHandled = true;
                    }
                } else {
                    block();
                }
            } else {
                AgentHelper.sendMessage(
                        myAgent,
                        getHouseholdAgentAIDList(),
                        "No Syncing Necessary",
                        ACLMessage.INFORM_IF
                );

                allSyncActionsHandled = true;
            }
        }

        @Override
        public boolean done() {
            return allSyncActionsHandled;
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done propagating");
            }

            return 0;
        }
    }

    public class InitiateSCExchangeBehaviour extends OneShotBehaviour {
        private final SequentialBehaviour exchange = new SequentialBehaviour();

        public InitiateSCExchangeBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            reset();

            exchange.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new InterestListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new SocialCapitaSyncPropagateBehaviour(myAgent));
            exchange.addSubBehaviour(new ExchangeRoundOverListener(myAgent));

            myAgent.addBehaviour(exchange);

            // Broadcast the start of the exchange round to all household agents
            AgentHelper.sendMessage(
                    myAgent,
                    getHouseholdAgentAIDList(),
                    "Exchange Initiated",
                    ACLMessage.REQUEST
            );
        }

        @Override
        public void reset() {
            super.reset();

        }
    }

    public class InterestListenerSCBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceiveTradeOffer = new ArrayList<>();

        public InterestListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
            boolean refuseRequest = true;

            ACLMessage interestMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (interestMessage != null && adverts.size() == config.getPopulationCount()) {
                // Check if the household agent has made interaction with another household agent in the current exchange round
                if (!householdAgentsInteractions.get(interestMessage.getSender())) {
                    // Flip the "made interaction" flag
                    householdAgentsInteractions.replace(interestMessage.getSender(), true);

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> sendersAdvertisedTimeSlots = adverts.get(interestMessage.getSender());

                    // Find out if the sender has any timeslots available to trade
                    if (!sendersAdvertisedTimeSlots.isEmpty()) {
                        // Make sure the incoming object is readable
                        Serializable incomingObject = null;

                        try {
                            incomingObject = interestMessage.getContentObject();
                        } catch (UnreadableException e) {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming message about an interest in timeslots is unreadable: " + e.getMessage());
                        }

                        if (incomingObject != null) {
                            // Make sure the incoming object is of the expected type and the advert is not empty
                            if (incomingObject instanceof SerializableTimeSlotArray) {
                                TimeSlot[] desiredTimeSlots = ((SerializableTimeSlotArray) incomingObject).timeSlots();
                                TimeSlot targetTimeSlot = null;
                                AID targetOwner = null;

                                // TODO: Cite Arena code
                                ArrayList<AID> shuffledAdvertPosters = new ArrayList<>(adverts.keySet());

                                // Remove the requesting agent from the temp advert catalogue to avoid an unnecessary check
                                shuffledAdvertPosters.remove(interestMessage.getSender());
                                Collections.shuffle(shuffledAdvertPosters, config.getRandom());

                                // Find the desired timeslot in the published adverts
                                browsingTimeSlots:
                                for (TimeSlot desiredTimeSlot : desiredTimeSlots) {
                                    for (AID advertPoster : shuffledAdvertPosters) {
                                        // Check if the potential receiving household agent has made an interaction in the current exchange round
                                        if (!householdAgentsInteractions.get(advertPoster)) {
                                            ArrayList<TimeSlot> timeSlotsForTrade = adverts.get(advertPoster);

                                            for (TimeSlot timeSlotForTrade : timeSlotsForTrade) {
                                                if (desiredTimeSlot.equals(timeSlotForTrade)) {
                                                    targetTimeSlot = timeSlotForTrade;
                                                    targetOwner = advertPoster;

                                                    // Add the target agent to the list of agents to receive a trade offer
                                                    agentsToReceiveTradeOffer.add(advertPoster);
                                                    // Flip the target agent's "made interaction" flag to true so that
                                                    // it does not get paired up with other agents this round
                                                    householdAgentsInteractions.replace(advertPoster, true);

                                                    break browsingTimeSlots;
                                                }
                                            }
                                        } else {
                                            // TODO: let the requesting agent know that the receiving agent is already occupied this round
                                        }
                                    }
                                }

                                // Check if the sender has any timeslots to offer in return and if a desired timeslot was found
                                if (targetTimeSlot != null) {
                                    // Offer the sender's least wanted timeslot - the first element of the advert
                                    // Send the trade offer to the agent that has the desired timeslot, with the
                                    // sender's nickname as the text content
                                    AgentHelper.sendMessage(
                                            myAgent,
                                            targetOwner,
                                            "New Offer",
                                            new TradeOffer(
                                                    interestMessage.getSender(),
                                                    targetOwner,
                                                    sendersAdvertisedTimeSlots.getFirst(),
                                                    targetTimeSlot
                                            ),
                                            ACLMessage.PROPOSE
                                    );

                                    numOfTradesStarted++;
                                    refuseRequest = false;
                                }
                            } else {
                                AgentHelper.printAgentError(myAgent.getLocalName(), "Interest for timeslots cannot be processed: the received object has an incorrect type.");
                            }
                        }
                    }
                } else {
                    // TODO: let the requesting agent know that it has already made interaction therefore cannot make any more requests
                }

                numOfRequestsProcessed++;

                // After processing each CFP, check if all agents have sent this message
                final int populationCount = RunConfigurationSingleton.getInstance().getPopulationCount();

                if (numOfRequestsProcessed == populationCount && agentsToReceiveTradeOffer.size() <= populationCount) {
                    // By subtracting the arraylist of agents from the list of all agents, get the agents who did not
                    // receive a trade request in the current exchange round and notify them.
                    agentsToNotify = new ArrayList<>(getHouseholdAgentAIDList());
                    agentsToNotify.removeAll(agentsToReceiveTradeOffer);

                    // Broadcast the "no offers" message to the agents who did not receive a trade offer for various reasons
                    AgentHelper.sendMessage(
                            myAgent,
                            agentsToNotify,
                            "No Expected Offers This Round",
                            ACLMessage.INFORM
                    );
                }

                if (refuseRequest) {
                    // Reach this part if any of these events happened:
                    // - the sender had no timeslots advertised to offer in return
                    // - the object sent by the requester can't be processed
                    // - the recipient of the request has already made interaction with another agent this round
                    // - none of the desired timeslots were found in the adverts
                    AgentHelper.sendMessage(
                            myAgent,
                            interestMessage.getSender(),
                            "Desired Timeslots Not Available",
                            ACLMessage.REFUSE
                    );
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return numOfRequestsProcessed == RunConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return 0;
        }
    }

    public class ExchangeRoundOverListener extends Behaviour {
        private int numOfHouseholdAgentsFinished = 0;

        public ExchangeRoundOverListener(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage doneWithExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Done");

            if (doneWithExchangeMessage != null) {
                // Make sure the incoming object is readable
                Serializable incomingObject = null;

                try {
                    incomingObject = doneWithExchangeMessage.getContentObject();
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming agent contact is unreadable: " + e.getMessage());
                }

                if (incomingObject != null) {
                    // Make sure the incoming object is of the expected type
                    if (incomingObject instanceof AgentContact) {
                        // Update the agent contact details with its current values
                        for (AgentContact contact : householdAgentContacts) {
                            if (contact.getAgentIdentifier() == ((AgentContact)incomingObject).getAgentIdentifier()) {
                                contact.setCurrentSatisfaction(((AgentContact)incomingObject).getCurrentSatisfaction());
                            }
                        }
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Household contacts cannot be updated: the received object has an incorrect type.");
                    }
                }

                numOfHouseholdAgentsFinished++;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return numOfHouseholdAgentsFinished == RunConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(
                        myAgent.getLocalName(),
                        "Exchange round over." +
                                " | Trades started: " + numOfTradesStarted +
                                " | Successful exchanges: " + numOfSuccessfulExchanges
                );
            }

            if (numOfSuccessfulExchanges == 0) {
                exchangeTimeout++;
            } else {
                exchangeTimeout = 0;
            }

            if (exchangeTimeout == 10) {
                SequentialBehaviour endOfDaySequence = new SequentialBehaviour();

                endOfDaySequence.addSubBehaviour(new InitiateSocialLearningBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new SocialLearningOverListenerBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new CallItADayBehaviour(myAgent));

                myAgent.addBehaviour(endOfDaySequence);
            } else {
                switch (RunConfigurationSingleton.getInstance().getExchangeType()) {
                    case MessagePassing -> myAgent.addBehaviour(new InitiateExchangeBehaviour(myAgent));
                    case SmartContract -> myAgent.addBehaviour(new InitiateSCExchangeBehaviour(myAgent));
                }

            }

            return 0;
        }
    }

    public class InitiateSocialLearningBehaviour extends OneShotBehaviour {
        public InitiateSocialLearningBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

            // TODO: Cite Arena code
            // Copy agents to store all agents that haven't yet been selected for social learning.
            ArrayList<AID> unselectedAgents = new ArrayList<>(getHouseholdAgentAIDList());

            // Agents who mutated can't do social learning.
            int learningSize = config.getNumOfAgentsToEvolve();

            if (unselectedAgents.size() < learningSize) {
                learningSize = unselectedAgents.size();
            }

            Collections.shuffle(unselectedAgents, config.getRandom());

            for (int i = 0; i < learningSize; i++) {
                // Assign the selected agent another agents performance to 'retrospectively' observe.
                int observedPerformanceIndex = config.getRandom().nextInt(config.getPopulationCount());

                // Ensure the agent altering its strategy doesn't copy itself.
                while (i == observedPerformanceIndex) {
                    observedPerformanceIndex = config.getRandom().nextInt(config.getPopulationCount());
                }

                // Send the observed agent's contact to the agent selected to learn
                AgentHelper.sendMessage(
                        myAgent,
                        getHouseholdAgentAIDList().get(i),
                        "Selected for Social Learning",
                        householdAgentContacts.get(observedPerformanceIndex),
                        ACLMessage.QUERY_IF
                );

                unselectedAgents.remove(getHouseholdAgentAIDList().get(i));
            }

            if (!unselectedAgents.isEmpty()) {
                // Broadcast to the rest of the agents that they have not been selected for social learning today
                AgentHelper.sendMessage(
                        myAgent,
                        unselectedAgents,
                        "Not Selected for Social Learning",
                        ACLMessage.QUERY_IF
                );
            }
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "initiated social learning");
            }

            return 0;
        }
    }

    public class SocialLearningOverListenerBehaviour extends Behaviour {
        private int socialLearningOverMessagesReceived = 0;

        public SocialLearningOverListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage socialLearningOverMessage = AgentHelper.receiveMessage(myAgent, "Social Learning Done");

            if (socialLearningOverMessage != null) {
                // Make sure the incoming object is readable
                Serializable incomingObject = null;

                try {
                    incomingObject = socialLearningOverMessage.getContentObject();
                } catch (UnreadableException e) {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming agent contact is unreadable: " + e.getMessage());
                }

                if (incomingObject != null) {
                    if (incomingObject instanceof AgentContact) {
                        for (AgentContact contact : householdAgentContacts) {
                            if (contact.getAgentIdentifier().equals(((AgentContact)incomingObject).getAgentIdentifier())) {
                                contact.setType(((AgentContact)incomingObject).getType());
                                contact.setCurrentSatisfaction(((AgentContact)incomingObject).getCurrentSatisfaction());

                                break;
                            }
                        }
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "The changes after social learning cannot be reflected: the received object has an incorrect type.");
                    }
                }

                socialLearningOverMessagesReceived++;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return socialLearningOverMessagesReceived == RunConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done waiting for social learning to finish");
            }

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
                    tickerAgent,
                    "Done",
                    new SerializableAgentContactList(new ArrayList<>(householdAgentContacts)),
                    ACLMessage.INFORM
            );
        }
    }

    private void initialAgentSetup() {
        availableTimeSlots = new ArrayList<>();
        adverts = new HashMap<>();
        numOfTradesStarted = 0;
        numOfSuccessfulExchanges = 0;
        agentsToNotify = new ArrayList<>();
        exchangeTimeout = 0;

        householdAgentContacts = new ArrayList<>();
        householdAgentsInteractions = new HashMap<>();
    }

    private ArrayList<AID> getHouseholdAgentAIDList() {
        return new ArrayList<>(this.householdAgentsInteractions.keySet());
    }

    private void exchangeValuesReset() {
        this.adverts.clear();
        this.numOfTradesStarted = 0;
        this.numOfSuccessfulExchanges = 0;
        this.agentsToNotify.clear();
        this.householdAgentsInteractions.clear();

        // Shuffle the list of household agents before every exchange
        Collections.shuffle(this.householdAgentContacts, RunConfigurationSingleton.getInstance().getRandom());

        // Reset each household agent's "made interaction" flag to false
        // By recreating the hashmap that holds the (AID, Boolean) pairs
        for (AgentContact contact : this.householdAgentContacts) {
            this.householdAgentsInteractions.put(contact.getAgentIdentifier(), false);
        }
    }
}
