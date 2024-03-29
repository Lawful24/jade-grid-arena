package com.napier.agents;

import com.napier.*;
import com.napier.concepts.*;
import com.napier.singletons.SimulationConfigurationSingleton;
import com.napier.singletons.DataOutputSingleton;
import com.napier.singletons.TickerTrackerSingleton;
import com.napier.types.AgentStrategyType;
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
    private HashMap<AID, ArrayList<TimeSlot>> initialRandomAllocatedTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> requestedTimeSlots;
    private double initialRandomAllocationAverageSatisfaction;
    private double optimumAveragePossibleSatisfaction;
    private HashMap<AID, ArrayList<TimeSlot>> adverts;
    private int numOfTradesStarted;
    private int numOfSuccessfulExchanges;
    private ArrayList<AID> agentsToNotify;
    private int currentExchangeRound;
    private int exchangeTimeout;

    // Agent contact attributes
    private AID tickerAgent;
    private ArrayList<AgentContact> householdAgentContacts;
    private HashMap<AID, Boolean> householdAgentsInteractions;

    @Override
    protected void setup() {
        this.initialAgentSetup();

        AgentHelper.registerAgent(this, "Advertising-board");

        addBehaviour(new FindTickerBehaviour(this));
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

                        if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                            AgentHelper.printAgentLog(myAgent.getLocalName(), "Reset for new run");
                        }

                        myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                    }

                    // Set the daily resources and adverts to their initial values
                    this.reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new GenerateTimeSlotsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DistributeInitialRandomTimeSlotAllocations(myAgent));

                    switch (SimulationConfigurationSingleton.getInstance().getExchangeType()) {
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
            initialRandomAllocatedTimeSlots.clear();
            requestedTimeSlots.clear();
            initialRandomAllocationAverageSatisfaction = 0;
            optimumAveragePossibleSatisfaction = 0;
            adverts.clear();
            currentExchangeRound = 1;
            exchangeTimeout = 0;
        }
    }

    public class GenerateTimeSlotsBehaviour extends OneShotBehaviour {
        public GenerateTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

            // TODO: Cite Arena code
            // Fill the available time-slots with all the slots that exist each day.
            int numOfRequiredTimeSlots = config.getPopulationCount() * config.getNumOfSlotsPerAgent();

            for (int i = 1; i <= numOfRequiredTimeSlots; i++) {
                // Selects a time-slot based on the demand curve.
                int wheelSelector = config.getRandom().nextInt(config.getTotalAvailableEnergy());
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
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

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

                initialRandomAllocatedTimeSlots.put(contact.getAgentIdentifier(), new ArrayList<>(Arrays.asList(initialTimeSlots)));
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
            this.reset();

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
                Serializable receivedObject = AgentHelper.readReceivedContentObject(advertisingMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type and the advert is not empty
                if (receivedObject instanceof SerializableTimeSlotArray unwantedTimeSlotsHolder) {
                    // Register (or update) the advert
                    adverts.put(
                            advertisingMessage.getSender(),
                            new ArrayList<>(Arrays.asList(unwantedTimeSlotsHolder.timeSlots()))
                    );

                    numOfAdvertsReceived++;
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Advert cannot be registered: the received object has an incorrect type or is null.");
                }

                // This has to be integrated in this behaviour to make sure that the adverts have been collected
                if (numOfAdvertsReceived == SimulationConfigurationSingleton.getInstance().getPopulationCount() && householdAgentContacts.size() == numOfAdvertsReceived) {
                    // Shuffle the agent contact list before broadcasting the exchange open message
                    // This likely determines the order in which agents participate in the exchange
                    Collections.shuffle(householdAgentContacts, SimulationConfigurationSingleton.getInstance().getRandom());

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
            return numOfAdvertsReceived == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
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
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();
            boolean refuseRequest = true;

            ACLMessage interestMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (interestMessage != null && adverts.size() == config.getPopulationCount()) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(interestMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type and the advert is not empty
                if (receivedObject instanceof SerializableTimeSlotArray requestedTimeSlotsHolder) {
                    TimeSlot targetTimeSlot = null;
                    AID targetOwner = null;

                    AdvertisingBoardAgent.this.requestedTimeSlots.put(interestMessage.getSender(), new ArrayList<>(Arrays.asList(requestedTimeSlotsHolder.timeSlots())));

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> sendersAdvertisedTimeSlots = adverts.get(interestMessage.getSender());

                    // Check if the household agent has made interaction with another household agent in the current exchange round
                    // Find out if the sender has any timeslots available to trade
                    if (!householdAgentsInteractions.get(interestMessage.getSender()) && !sendersAdvertisedTimeSlots.isEmpty()) {
                        // Flip the "made interaction" flag
                        householdAgentsInteractions.replace(interestMessage.getSender(), true);

                        // TODO: Cite Arena code
                        ArrayList<AID> shuffledAdvertPosters = new ArrayList<>(adverts.keySet());

                        // Remove the requesting agent from the temp advert catalogue to avoid an unnecessary check
                        shuffledAdvertPosters.remove(interestMessage.getSender());
                        Collections.shuffle(shuffledAdvertPosters, config.getRandom());

                        // Find the desired timeslot in the published adverts
                        browsingTimeSlots:
                        for (TimeSlot desiredTimeSlot : requestedTimeSlotsHolder.timeSlots()) {
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
                        // TODO: let the requesting agent know that it has already made interaction therefore cannot make any more requests
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Interest for timeslots cannot be processed: the received object has an incorrect type or is null.");
                }

                numOfRequestsProcessed++;

                // After processing each CFP, check if all agents have sent this message
                final int populationCount = SimulationConfigurationSingleton.getInstance().getPopulationCount();

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
            return numOfRequestsProcessed == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (currentExchangeRound == 1) {
                calculateInitialAndOptimumSatisfactions();
            }

            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
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
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOfferResponseMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer tradeOfferResponse) {
                        if (tradeOfferResponseMessage.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                            // Handle the accepted trade offer
                            // Remove the traded timeslots from the adverts
                            adverts.get(tradeOfferResponseMessage.getSender()).remove(tradeOfferResponse.timeSlotRequested());
                            adverts.get(((TradeOffer) receivedObject).requesterAgent()).remove(tradeOfferResponse.timeSlotOffered());

                            // Notify the agent who initiated the interest
                            AgentHelper.sendMessage(
                                    myAgent,
                                    ((TradeOffer) receivedObject).requesterAgent(),
                                    tradeOfferResponseMessage.getConversationId(),
                                    receivedObject,
                                    ACLMessage.AGREE
                            );

                            numOfSuccessfulExchanges++;
                        } else {
                            AgentHelper.sendMessage(
                                    myAgent,
                                    ((TradeOffer) receivedObject).requesterAgent(),
                                    "Trade Rejected",
                                    ACLMessage.CANCEL
                            );

                            agentsToNotify.add(tradeOfferResponseMessage.getSender());
                        }
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Trade offer response cannot be acted upon: the received object has an incorrect type or is null.");
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
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for trades");
            }

            return 0;
        }
    }

    public class SocialCapitaSyncPropagateBehaviour extends Behaviour {
        private int numOfMessagesPropagated = 0;

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
                        Serializable receivedObject = AgentHelper.readReceivedContentObject(incomingSyncMessage, myAgent.getLocalName(), AID.class);

                        // Make sure the incoming object is of the expected type
                        if (receivedObject instanceof AID receiverAgentIdentifier) {
                            AgentHelper.sendMessage(
                                    myAgent,
                                    receiverAgentIdentifier,
                                    incomingSyncMessage.getConversationId(),
                                    ACLMessage.INFORM_IF
                            );
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Agent social capita cannot be synced: the received object has an incorrect type or is null.");
                        }
                    }

                    numOfMessagesPropagated++;
                } else {
                    block();
                }
            }
        }

        @Override
        public boolean done() {
            return numOfMessagesPropagated == numOfSuccessfulExchanges;
        }

        @Override
        public int onEnd() {
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
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
            this.reset();

            exchange.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
            exchange.addSubBehaviour(new InterestListenerSCBehaviour(myAgent));
            exchange.addSubBehaviour(new StartedTradesOutcomeSCListener(myAgent));
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

    public class InterestListenerSCBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceiveTradeOffer = new ArrayList<>();

        public InterestListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();
            boolean refuseRequest = true;

            ACLMessage interestMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (interestMessage != null && adverts.size() == config.getPopulationCount()) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(interestMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type and the advert is not empty
                if (receivedObject instanceof SerializableTimeSlotArray requestedTimeSlotsHolder) {
                    TimeSlot targetTimeSlot = null;
                    AID targetOwner = null;

                    requestedTimeSlots.put(interestMessage.getSender(), new ArrayList<>(Arrays.asList(requestedTimeSlotsHolder.timeSlots())));

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> sendersAdvertisedTimeSlots = adverts.get(interestMessage.getSender());

                    // Check if the household agent has made interaction with another household agent in the current exchange round
                    // Find out if the sender has any timeslots available to trade
                    if (!householdAgentsInteractions.get(interestMessage.getSender()) && !sendersAdvertisedTimeSlots.isEmpty()) {
                        // Flip the "made interaction" flag
                        householdAgentsInteractions.replace(interestMessage.getSender(), true);

                        // TODO: Cite Arena code
                        ArrayList<AID> shuffledAdvertPosters = new ArrayList<>(adverts.keySet());

                        // Remove the requesting agent from the temp advert catalogue to avoid an unnecessary check
                        shuffledAdvertPosters.remove(interestMessage.getSender());
                        Collections.shuffle(shuffledAdvertPosters, config.getRandom());

                        // Find the desired timeslot in the published adverts
                        browsingTimeSlots:
                        for (TimeSlot desiredTimeSlot : requestedTimeSlotsHolder.timeSlots()) {
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

                        // Check if the requester has any timeslots to offer in return and if a desired timeslot was found
                        if (targetTimeSlot != null) { // TODO: change all occurrences of "sender" to "requester"
                            // Offer the requester's least wanted timeslot - the first element of the advert
                            // Send the created trade offer object to the requester agent so that it can forward
                            // it to the target agent.
                            AgentHelper.sendMessage(
                                    myAgent,
                                    interestMessage.getSender(),
                                    "Offer Created",
                                    new TradeOffer(
                                            interestMessage.getSender(),
                                            targetOwner,
                                            sendersAdvertisedTimeSlots.getFirst(),
                                            targetTimeSlot
                                    ),
                                    ACLMessage.AGREE
                            );

                            numOfTradesStarted++;
                            refuseRequest = false;
                        }
                    } else {
                        // TODO: let the requesting agent know that it has already made interaction therefore cannot make any more requests
                        // TODO: this means that this agent could be a receiver
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Interest for timeslots cannot be processed: the received object has an incorrect type or is null.");
                }

                numOfRequestsProcessed++;

                // After processing each CFP, check if all agents have sent this message
                final int populationCount = SimulationConfigurationSingleton.getInstance().getPopulationCount();

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
            return numOfRequestsProcessed == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (currentExchangeRound == 1) {
                calculateInitialAndOptimumSatisfactions();
            }

            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return 0;
        }
    }

    public class StartedTradesOutcomeSCListener extends Behaviour {
        private int numOfOutcomesReceived = 0;

        public StartedTradesOutcomeSCListener(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (numOfTradesStarted > 0) {
                ACLMessage tradeOutcomeMessage = AgentHelper.receiveMessage(myAgent, "Trade Outcome");

                if (tradeOutcomeMessage != null) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOutcomeMessage, myAgent.getLocalName(), TradeOffer.class, String.class);

                    if (!receivedObject.equals("Rejected")) {
                        // Make sure the incoming object is of the expected type
                        if (receivedObject instanceof TradeOffer acceptedTradeOffer) {
                            // Handle the accepted trade offer
                            // Remove the traded timeslots from the adverts
                            adverts.get(tradeOutcomeMessage.getSender()).remove(acceptedTradeOffer.timeSlotOffered());
                            adverts.get(acceptedTradeOffer.receiverAgent()).remove(acceptedTradeOffer.timeSlotRequested());

                            numOfSuccessfulExchanges++;
                        } else {
                            AgentHelper.printAgentError(myAgent.getLocalName(), "Started trade offer cannot be processed: the received object has an incorrect type or is null.");
                        }
                    }

                    numOfOutcomesReceived++;
                } else {
                    block();
                }
            }
        }

        @Override
        public boolean done() {
            return numOfOutcomesReceived == numOfTradesStarted;
        }

        @Override
        public int onEnd() {
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done updating adverts based on started trades");
            }

            return 0;
        }
    }

    public class ExchangeRoundOverListener extends Behaviour {
        private final HashMap<AID, EndOfExchangeHouseholdDataHolder> dataHolders = new HashMap<>();

        public ExchangeRoundOverListener(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage doneWithExchangeMessage = AgentHelper.receiveMessage(myAgent, "Exchange Done");

            if (doneWithExchangeMessage != null) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(doneWithExchangeMessage, myAgent.getLocalName(), EndOfDayHouseholdAgentDataHolder.class);

                // Make sure the incoming object is of the expected type
                if (receivedObject instanceof EndOfExchangeHouseholdDataHolder householdAgentDataHolder) {
                    // Update the agent contact details with its current values
                    for (AgentContact contact : householdAgentContacts) {
                        if (contact.getAgentIdentifier().equals(doneWithExchangeMessage.getSender())) {
                            contact.setCurrentSatisfaction(householdAgentDataHolder.satisfaction());
                        }
                    }

                    this.dataHolders.put(doneWithExchangeMessage.getSender(), householdAgentDataHolder);
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "The exchange round cannot be cannot be ended: the received object has an incorrect type or is null.");
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return dataHolders.size() == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(
                        myAgent.getLocalName(),
                        "Exchange round " + currentExchangeRound +  " over." +
                                " | Trades started: " + numOfTradesStarted +
                                " | Successful exchanges: " + numOfSuccessfulExchanges
                );
            }

            if (numOfSuccessfulExchanges == 0) {
                exchangeTimeout++;
            } else {
                exchangeTimeout = 0;
            }

            for (AgentStrategyType agentStrategyType : AgentStrategyType.values()) {
                DataOutputSingleton.getInstance().appendExchangeData(
                        TickerTrackerSingleton.getInstance().getCurrentSimulationRun(),
                        TickerTrackerSingleton.getInstance().getCurrentDay(),
                        currentExchangeRound,
                        agentStrategyType,
                        AgentHelper.averageAgentSatisfaction(householdAgentContacts, agentStrategyType)
                );
            }

            for (AID householdAgent : this.dataHolders.keySet()) {
                AgentStrategyType strategyType = null;

                for (AgentContact householdAgentContact : householdAgentContacts) {
                    if (householdAgentContact.getAgentIdentifier().equals(householdAgent)) {
                        strategyType = householdAgentContact.getType();

                        break;
                    }
                }

                DataOutputSingleton.getInstance().appendPerformanceData(
                        TickerTrackerSingleton.getInstance().getCurrentSimulationRun(),
                        TickerTrackerSingleton.getInstance().getCurrentDay(),
                        currentExchangeRound,
                        strategyType,
                        dataHolders.get(householdAgent).isTradeOfferReceiver(),
                        dataHolders.get(householdAgent).exchangeRoundHouseholdCPUTime()
                );
            }

            if (exchangeTimeout == 10) {
                SequentialBehaviour endOfDaySequence = new SequentialBehaviour();

                endOfDaySequence.addSubBehaviour(new InitiateSocialLearningBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new SocialLearningOverListenerBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new CallItADayBehaviour(myAgent));

                myAgent.addBehaviour(endOfDaySequence);
            } else {
                currentExchangeRound++;

                switch (SimulationConfigurationSingleton.getInstance().getExchangeType()) {
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
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

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
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
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
                Serializable receivedObject = AgentHelper.readReceivedContentObject(socialLearningOverMessage, myAgent.getLocalName(), AgentContact.class);

                if (receivedObject instanceof AgentContact agentContactAfterSocialLearning) {
                    for (AgentContact contact : householdAgentContacts) {
                        if (contact.getAgentIdentifier().equals(agentContactAfterSocialLearning.getAgentIdentifier())) {
                            contact.setType(agentContactAfterSocialLearning.getType());
                            contact.setCurrentSatisfaction(agentContactAfterSocialLearning.getCurrentSatisfaction());

                            break;
                        }
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "The changes after social learning cannot be reflected: the received object has an incorrect type or is null.");
                }

                socialLearningOverMessagesReceived++;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return socialLearningOverMessagesReceived == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done waiting for social learning to finish");
            }

            return 0;
        }
    }

    public class CallItADayBehaviour extends Behaviour {
        private final HashMap<AgentContact, EndOfDayHouseholdAgentDataHolder> householdAgentsEndOfDayData = new HashMap<>();

        public CallItADayBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ACLMessage householdDoneMessage = AgentHelper.receiveMessage(myAgent, "Done");

            if (householdDoneMessage != null) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(householdDoneMessage, myAgent.getLocalName(), EndOfDayHouseholdAgentDataHolder.class);

                if (receivedObject instanceof EndOfDayHouseholdAgentDataHolder householdAgentDataHolder) {
                    AgentContact doneHouseholdContact = null;

                    for (AgentContact householdAgentContact : householdAgentContacts) {
                        if (householdAgentContact.getAgentIdentifier().equals(householdDoneMessage.getSender())) {
                            doneHouseholdContact = householdAgentContact;
                        }
                    }

                    if (doneHouseholdContact != null) {
                        householdAgentsEndOfDayData.put(doneHouseholdContact, householdAgentDataHolder);
                    } else {
                        AgentHelper.printAgentError(myAgent.getLocalName(), "Could not append household agent data to the data file: household agent was not found in the contacts.");
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "The end of day household agent data cannot be processed: the received object has an incorrect type or is null.");
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return householdAgentsEndOfDayData.size() == SimulationConfigurationSingleton.getInstance().getPopulationCount();
        }

        @Override
        public int onEnd() {
            SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

            double overallRunSatisfactionSum = 0;
            double socialAgentsRunSatisfactionSum = 0;
            int numOfSocialAgents = 0;

            for (AgentContact householdAgentContact : householdAgentContacts) {
                if (householdAgentContact.getType() == AgentStrategyType.SOCIAL) {
                    socialAgentsRunSatisfactionSum += householdAgentContact.getCurrentSatisfaction();
                    numOfSocialAgents++;
                }

                overallRunSatisfactionSum += householdAgentContact.getCurrentSatisfaction();
            }

            double averageSocialSatisfaction = socialAgentsRunSatisfactionSum / (double)config.getPopulationCount();
            double averageSelfishSatisfaction = (overallRunSatisfactionSum - socialAgentsRunSatisfactionSum) / (double)config.getPopulationCount(); // TODO: is this correct?
            double averageSocialSatisfactionStandardDeviation = AgentHelper.averageSatisfactionStandardDeviation(householdAgentContacts, AgentStrategyType.SOCIAL, overallRunSatisfactionSum / (double)config.getPopulationCount());
            double averageSelfishSatisfactionStandardDeviation = AgentHelper.averageSatisfactionStandardDeviation(householdAgentContacts, AgentStrategyType.SELFISH, overallRunSatisfactionSum / (double)config.getPopulationCount());
            AgentStatisticalValuesPerStrategyType socialStatisticalValues = new AgentStatisticalValuesPerStrategyType(householdAgentContacts, AgentStrategyType.SOCIAL);
            AgentStatisticalValuesPerStrategyType selfishStatisticalValues = new AgentStatisticalValuesPerStrategyType(householdAgentContacts, AgentStrategyType.SELFISH);

            DataOutputSingleton.getInstance().appendDailyData(
                    TickerTrackerSingleton.getInstance().getCurrentSimulationRun(),
                    TickerTrackerSingleton.getInstance().getCurrentDay(),
                    numOfSocialAgents,
                    config.getPopulationCount() - numOfSocialAgents,
                    averageSocialSatisfaction,
                    averageSelfishSatisfaction,
                    averageSocialSatisfactionStandardDeviation,
                    averageSelfishSatisfactionStandardDeviation,
                    socialStatisticalValues,
                    selfishStatisticalValues,
                    initialRandomAllocationAverageSatisfaction,
                    optimumAveragePossibleSatisfaction
            );

            for (AgentContact householdAgentContact : householdAgentContacts) {
                DataOutputSingleton.getInstance().appendAgentData(
                        TickerTrackerSingleton.getInstance().getCurrentSimulationRun(),
                        TickerTrackerSingleton.getInstance().getCurrentDay(),
                        householdAgentContact.getType(),
                        householdAgentContact.getCurrentSatisfaction(),
                        householdAgentsEndOfDayData.get(householdAgentContact).numOfDailyRejectedReceivedExchanges(),
                        householdAgentsEndOfDayData.get(householdAgentContact).numOfDailyRejectedRequestedExchanges(),
                        householdAgentsEndOfDayData.get(householdAgentContact).numOfDailyAcceptedRequestedExchanges(),
                        householdAgentsEndOfDayData.get(householdAgentContact).numOfDailyAcceptedReceivedExchangesWithSocialCapita(),
                        householdAgentsEndOfDayData.get(householdAgentContact).numOfDailyAcceptedReceivedExchangesWithoutSocialCapita(),
                        householdAgentsEndOfDayData.get(householdAgentContact).totalSocialCapita()
                );
            }

            EndOfDayAdvertisingBoardDataHolder endOfDayData = new EndOfDayAdvertisingBoardDataHolder(
                    householdAgentContacts,
                    numOfSocialAgents,
                    config.getPopulationCount() - numOfSocialAgents,
                    averageSocialSatisfaction,
                    averageSelfishSatisfaction,
                    averageSocialSatisfactionStandardDeviation,
                    averageSelfishSatisfactionStandardDeviation,
                    socialStatisticalValues,
                    selfishStatisticalValues,
                    initialRandomAllocationAverageSatisfaction,
                    optimumAveragePossibleSatisfaction
            );

            AgentHelper.sendMessage(
                    myAgent,
                    tickerAgent,
                    "Done",
                    endOfDayData,
                    ACLMessage.INFORM
            );

            return 0;
        }
    }

    private void initialAgentSetup() {
        this.availableTimeSlots = new ArrayList<>();
        this.initialRandomAllocatedTimeSlots = new HashMap<>();
        this.adverts = new HashMap<>();
        this.requestedTimeSlots = new HashMap<>();
        this.numOfTradesStarted = 0;
        this.numOfSuccessfulExchanges = 0;
        this.agentsToNotify = new ArrayList<>();
        this.exchangeTimeout = 0;

        this.householdAgentContacts = new ArrayList<>();
        this.householdAgentsInteractions = new HashMap<>();
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
        Collections.shuffle(this.householdAgentContacts, SimulationConfigurationSingleton.getInstance().getRandom());

        // Reset each household agent's "made interaction" flag to false
        // By recreating the hashmap that holds the (AID, Boolean) pairs
        for (AgentContact contact : this.householdAgentContacts) {
            this.householdAgentsInteractions.put(contact.getAgentIdentifier(), false);
        }
    }

    private void calculateInitialAndOptimumSatisfactions() {
        // TODO: Cite Arena code
        ArrayList<TimeSlot> allAllocatedTimeSlots = new ArrayList<>();
        ArrayList<TimeSlot> allRequestedTimeSlots = new ArrayList<>();

        for (AgentContact householdAgentContact : this.householdAgentContacts) {
            allAllocatedTimeSlots.addAll(this.initialRandomAllocatedTimeSlots.get(householdAgentContact.getAgentIdentifier()));
            allRequestedTimeSlots.addAll(this.requestedTimeSlots.get(householdAgentContact.getAgentIdentifier()));
        }

        this.initialRandomAllocationAverageSatisfaction = AgentHelper.calculateCurrentAverageAgentSatisfaction(this.householdAgentContacts);
        this.optimumAveragePossibleSatisfaction = AgentHelper.calculateOptimumPossibleSatisfaction(allAllocatedTimeSlots, allRequestedTimeSlots);
    }
}
