package com.napier.agents;

import com.napier.AgentHelper;

import com.napier.concepts.AgentContact;
import com.napier.concepts.dataholders.AgentStatisticalValuesPerStrategyType;
import com.napier.concepts.dataholders.EndOfDayAdvertisingBoardDataHolder;
import com.napier.concepts.dataholders.EndOfDayHouseholdAgentDataHolder;
import com.napier.concepts.dataholders.EndOfExchangeHouseholdDataHolder;
import com.napier.concepts.dataholders.SerializableTimeSlotArray;
import com.napier.concepts.TimeSlot;
import com.napier.concepts.TradeOffer;
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
import org.glassfish.pfl.basic.contain.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

/**
 * An agent of the advertiser archetype. Its main purpose is to help Household agents advertise their timeslots
 * and conduct trades between them as the third party.
 * Only one of them can exist in this application.
 *
 * @author László Tárkányi
 */
public class AdvertisingBoardAgent extends Agent {
    // Generated attributes
    private ArrayList<TimeSlot> availableTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> initialRandomAllocatedTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> requestedTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> adverts;

    // Calculated attributes
    private double initialRandomAllocationAverageSatisfaction;
    private double optimumAveragePossibleSatisfaction;

    // Exchange Statistics/Tracker attributes
    private int numOfTradesStarted;
    private int numOfSuccessfulExchanges;
    private ArrayList<AID> agentsToNotify;

    // Daily Statistics/Tracker attributes
    private int numOfAgentsSelectedForSocialLearning;
    private int currentExchangeRound;
    private int exchangeTimeout;

    // Agent contact attributes
    private AID tickerAgent;
    private ArrayList<AgentContact> householdAgentContacts;
    private HashMap<AID, Boolean> householdAgentsInteractions;

    // Singletons
    private SimulationConfigurationSingleton config;
    private TickerTrackerSingleton timeTracker;
    private DataOutputSingleton outputInstance;

    @Override
    protected void setup() {
        this.initialAgentSetup();

        AgentHelper.registerAgent(this, "Advertising-board");

        // Add the initial behaviours
        addBehaviour(new FindTickerBehaviour(this));
        addBehaviour(new TickerDailyBehaviour(this));
    }

    @Override
    protected void takeDown() {
        AgentHelper.printAgentLog(getLocalName(), "Terminating...");
        AgentHelper.deregisterAgent(this);
    }

    /**
     * Seeks out the only Ticker type agent and saves it for contacting it in the future.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class FindTickerBehaviour extends OneShotBehaviour {
        public FindTickerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            tickerAgent = AgentHelper.saveAgentContacts(myAgent, "Ticker").getFirst().getAgentIdentifier();
        }
    }

    /**
     * Seeks out all the Household type agents and save them for contacting them in the future.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class FindHouseholdsBehaviour extends OneShotBehaviour {
        public FindHouseholdsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Populate the contact collection
            householdAgentContacts = AgentHelper.saveAgentContacts(myAgent, "Household");
        }
    }

    /**
     * A listener for messages from the Ticker agent. It is also responsible for initiating the agent's daily activities.
     * A repeated behaviour of AdvertisingBoardAgent.
     */
    public class TickerDailyBehaviour extends CyclicBehaviour {
        public TickerDailyBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for the tick that starts the day
            ACLMessage tick = AgentHelper.receiveMessage(myAgent, tickerAgent, ACLMessage.INFORM);

            if (tick != null) {
                // Check if the agent was asked to be terminated
                // i.e. if the program is exiting
                if (!tick.getConversationId().equals("Terminate")) {
                    // Do a reset on all agent attributes on each new simulation run
                    if (tick.getConversationId().equals("New Run")) {
                        initialAgentSetup();

                        if (config.isDebugMode()) {
                            AgentHelper.printAgentLog(myAgent.getLocalName(), "Reset for new run");
                        }

                        // Update the Household agent contacts
                        myAgent.addBehaviour(new FindHouseholdsBehaviour(myAgent));
                    }

                    // Set the daily resources and adverts to their initial values
                    this.reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new GenerateTimeSlotsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DistributeInitialRandomTimeSlotAllocations(myAgent));

                    // Determine which exchange type to use based on the current value in the configuration
                    switch (config.getExchangeType()) {
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

            // Daily reset
            availableTimeSlots.clear();
            initialRandomAllocatedTimeSlots.clear();
            requestedTimeSlots.clear();
            initialRandomAllocationAverageSatisfaction = 0;
            optimumAveragePossibleSatisfaction = 0;
            adverts.clear();
            currentExchangeRound = 1;
            exchangeTimeout = 0;
            numOfAgentsSelectedForSocialLearning = 0;
        }
    }

    /**
     * Creates timeslot assets based on the configuration settings.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class GenerateTimeSlotsBehaviour extends OneShotBehaviour {
        public GenerateTimeSlotsBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            /*
            The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
            See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java
            */

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

    /**
     * Gives out an equal number of timeslots to all Household agents regardless of their preferences.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class DistributeInitialRandomTimeSlotAllocations extends OneShotBehaviour {
        public DistributeInitialRandomTimeSlotAllocations(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            Collections.shuffle(householdAgentContacts, config.getRandom());

            for (AgentContact contact : householdAgentContacts) {
                /*
                The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
                See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java
                */

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

                // Store the initially allocated timeslots for each agent
                initialRandomAllocatedTimeSlots.put(contact.getAgentIdentifier(), new ArrayList<>(Arrays.asList(initialTimeSlots)));
            }
        }
    }

    /**
     * Starts an exchange round of the Message Passing type on the side of the Advertising agent.
     * Sets off a chain of behaviours that represent the actions of this agent in an exchange round.
     * Specific to the Message Passing exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class InitiateExchangeBehaviour extends OneShotBehaviour {
        private final SequentialBehaviour exchangeRoundSequence = new SequentialBehaviour();

        public InitiateExchangeBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            this.reset();

            // Define the behaviours that should be used in a Message Passing exchange round
            exchangeRoundSequence.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new InquiryListenerBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new TradeOfferResponseListenerBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new SocialCapitaSyncPropagateBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new ExchangeRoundOverListener(myAgent));
            myAgent.addBehaviour(exchangeRoundSequence);

            // Broadcast the start of the exchange round to all Household agents
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

            resetExchange();
        }
    }

    /**
     * Listens for adverts from Household agents that contain the timeslots that they do not require.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class NewAdvertListenerBehaviour extends Behaviour {
        private int numOfAdvertsReceived = 0;

        public NewAdvertListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for requests from Household agents to advertise their unwanted timeslots
            ACLMessage advertisingMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.REQUEST);

            if (advertisingMessage != null) {
                // Make sure the incoming object is readable
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
                if (numOfAdvertsReceived == config.getPopulationCount() && householdAgentContacts.size() == numOfAdvertsReceived) {
                    // Shuffle the agent contact list before broadcasting the exchange open message
                    // This determines the order in which agents participate in the exchange
                    Collections.shuffle(householdAgentContacts, config.getRandom());

                    // Broadcast the opening of the exchange to all Household agents
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
            return numOfAdvertsReceived == config.getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for new adverts");
            }

            return 0;
        }
    }

    /**
     * Listens for incoming requests from Household agents regarding their timeslot preferences.
     * Specific to the Message Passing exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class InquiryListenerBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceiveATradeOffer = new ArrayList<>();

        public InquiryListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for calls for proposal from Household agents
            ACLMessage inquiryMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            // Wait until all agents had posted an advert
            if (inquiryMessage != null && adverts.size() == config.getPopulationCount()) {
                AID requesterAgent = inquiryMessage.getSender();
                boolean refuseRequest = true;

                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(inquiryMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type and the advert is not empty
                if (receivedObject instanceof SerializableTimeSlotArray requestedTimeSlotsHolder) {
                    // Store the requested timeslots
                    AdvertisingBoardAgent.this.requestedTimeSlots.put(requesterAgent, new ArrayList<>(Arrays.asList(requestedTimeSlotsHolder.timeSlots())));

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> requestersAdvertisedTimeSlots = adverts.get(requesterAgent);

                    // Check if the household agent has made interaction with another household agent in the current exchange round
                    // Find out if the requester has any timeslots available to trade
                    if (!householdAgentsInteractions.get(requesterAgent) && !requestersAdvertisedTimeSlots.isEmpty()) {
                        // Flip the "made interaction" flag
                        householdAgentsInteractions.replace(requesterAgent, true);

                        // Browse the advertised timeslots and try to find a requested timeslot
                        Pair<TimeSlot, AID> timeSlotOwnerPair = findRequestedTimeSlotInAdverts(
                                requestedTimeSlotsHolder.timeSlots(),
                                requesterAgent,
                                agentsToReceiveATradeOffer
                        );

                        // Check if the requester has any timeslots to offer in return and if a desired timeslot was found
                        if (timeSlotOwnerPair != null) {
                            AID receiverAgent = timeSlotOwnerPair.second();

                            // Offer the requester's least wanted timeslot - the first element of the advert
                            // Send the trade offer to the agent that has the desired timeslot, with the
                            // requester's nickname as the text content
                            AgentHelper.sendMessage(
                                    myAgent,
                                    receiverAgent,
                                    "New Offer",
                                    new TradeOffer(
                                            requesterAgent,
                                            receiverAgent,
                                            requestersAdvertisedTimeSlots.getFirst(),
                                            timeSlotOwnerPair.first()
                                    ),
                                    ACLMessage.PROPOSE
                            );

                            numOfTradesStarted++;
                            refuseRequest = false;
                        }
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Inquiry for timeslots cannot be processed: the received object has an incorrect type or is null.");
                }

                numOfRequestsProcessed++;

                // After processing each call for proposal, check if all agents have sent this message
                final int populationCount = config.getPopulationCount();

                // Check if all calls for proposal had been processed
                if (numOfRequestsProcessed == populationCount && agentsToReceiveATradeOffer.size() <= populationCount) {
                    // By subtracting the arraylist of agents from the list of all agents, get the agents who did not
                    // receive a trade request in the current exchange round and notify them.
                    agentsToNotify = new ArrayList<>(getHouseholdAgentAIDList());
                    agentsToNotify.removeAll(agentsToReceiveATradeOffer);

                    // Broadcast the "no offers" message to the agents who did not receive a trade offer for various reasons
                    AgentHelper.sendMessage(
                            myAgent,
                            agentsToNotify,
                            "No Expected Offers This Round",
                            ACLMessage.PROPOSE
                    );
                }

                if (refuseRequest) {
                    // Reach this block if any of these events happened:
                    // - the requester had no timeslots advertised to offer in return
                    // - the object sent by the requester can't be processed
                    // - the receiver has already made interaction with another agent this round
                    // - none of the desired timeslots were found in the adverts

                    // Send a reply to the requester agent about the request not being fulfilled
                    AgentHelper.sendMessage(
                            myAgent,
                            requesterAgent,
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
            return numOfRequestsProcessed == config.getPopulationCount();
        }

        @Override
        public int onEnd() {
            // At the first exchange of each day, calculate the initial and optimum agent satisfactions
            if (currentExchangeRound == 1) {
                calculateInitialAndOptimumSatisfactions();
            }

            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return 0;
        }
    }

    /**
     * Listens for the responses from the trade offers that were sent out as a result of the Household agent inquiries.
     * Specific to the Message Passing exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class TradeOfferResponseListenerBehaviour extends Behaviour {
        private int numOfTradeOfferReplies = 0;

        public TradeOfferResponseListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for the responses from the trade offer receivers
            ACLMessage tradeOfferResponseMessage = AgentHelper.receiveProposalReply(myAgent);

            if (tradeOfferResponseMessage != null) {
                // Check if the proposal was refused
                if (tradeOfferResponseMessage.getPerformative() != ACLMessage.REFUSE) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOfferResponseMessage, myAgent.getLocalName(), TradeOffer.class);

                    // Make sure the incoming object is of the expected type
                    if (receivedObject instanceof TradeOffer tradeOfferResponse) {
                        // Check if the trade was accepted
                        if (tradeOfferResponseMessage.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                            // Handle the accepted trade offer
                            // Remove the traded timeslots from the adverts
                            adverts.get(tradeOfferResponseMessage.getSender()).remove(tradeOfferResponse.timeSlotRequested());
                            adverts.get(((TradeOffer) receivedObject).requesterAgent()).remove(tradeOfferResponse.timeSlotOffered());

                            // Notify the agent who initiated the inquiry (the requester)
                            AgentHelper.sendMessage(
                                    myAgent,
                                    ((TradeOffer) receivedObject).requesterAgent(),
                                    tradeOfferResponseMessage.getConversationId(),
                                    receivedObject,
                                    ACLMessage.AGREE
                            );

                            numOfSuccessfulExchanges++;
                        } else {
                            // Notify the agent who initiated the inquiry (the requester)
                            AgentHelper.sendMessage(
                                    myAgent,
                                    ((TradeOffer) receivedObject).requesterAgent(),
                                    "Trade Rejected",
                                    ACLMessage.CANCEL
                            );

                            // Append the receiver agent to the list of agents to be notified in a different behaviour of the exchange
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
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for trades");
            }

            return 0;
        }
    }

    /**
     * Listens for messages to forward from the requester to the receiver after a successful trade.
     * Specific to the Message Passing exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class SocialCapitaSyncPropagateBehaviour extends Behaviour {
        private int numOfMessagesPropagated = 0;

        public SocialCapitaSyncPropagateBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Check if there were any successful exchanges
            // If there were none, this behaviour can be skipped
            if (numOfSuccessfulExchanges > 0) {
                // Listen for social capita messages to be forwarded
                ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.PROPAGATE);

                if (incomingSyncMessage != null) {
                    // Check if the trade offer receiver gains social capita
                    // If not, there is no reason to forward this message
                    if (!incomingSyncMessage.getConversationId().equals("No Syncing Necessary")) {
                        // Make sure the incoming object is readable
                        Serializable receivedObject = AgentHelper.readReceivedContentObject(incomingSyncMessage, myAgent.getLocalName(), AID.class);

                        // Make sure the incoming object is of the expected type
                        if (receivedObject instanceof AID receiverAgentIdentifier) {
                            // Notify the receiver about the gained social capita
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
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done propagating");
            }

            return 0;
        }
    }

    /**
     * Starts an exchange round of the Message Passing type on the side of the Advertising agent.
     * Sets off a chain of behaviours that represent the actions of this agent in an exchange round.
     * Specific to the Smart Contract exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class InitiateSCExchangeBehaviour extends OneShotBehaviour {
        private final SequentialBehaviour exchangeRoundSequence = new SequentialBehaviour();

        public InitiateSCExchangeBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            this.reset();

            // Define the behaviours that should be used in a Smart Contract exchange round
            exchangeRoundSequence.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new InquiryListenerSCBehaviour(myAgent));
            exchangeRoundSequence.addSubBehaviour(new StartedTradesOutcomeSCListener(myAgent));
            exchangeRoundSequence.addSubBehaviour(new ExchangeRoundOverListener(myAgent));
            myAgent.addBehaviour(exchangeRoundSequence);

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

            resetExchange();
        }
    }

    /**
     * Listens for incoming requests from Household agents regarding their timeslot preferences.
     * Specific to the Smart Contract exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class InquiryListenerSCBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceiveATradeOffer = new ArrayList<>();

        public InquiryListenerSCBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for calls for proposal from Household agents
            ACLMessage inquiryMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (inquiryMessage != null && adverts.size() == config.getPopulationCount()) {
                AID requesterAgent = inquiryMessage.getSender();
                boolean refuseRequest = true;

                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(inquiryMessage, myAgent.getLocalName(), SerializableTimeSlotArray.class);

                // Make sure the incoming object is of the expected type and the advert is not empty
                if (receivedObject instanceof SerializableTimeSlotArray requestedTimeSlotsHolder) {
                    requestedTimeSlots.put(requesterAgent, new ArrayList<>(Arrays.asList(requestedTimeSlotsHolder.timeSlots())));

                    // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                    ArrayList<TimeSlot> requestersAdvertisedTimeSlots = adverts.get(requesterAgent);

                    // Check if the household agent has made interaction with another household agent in the current exchange round
                    // Find out if the requester has any timeslots available to trade
                    if (!householdAgentsInteractions.get(requesterAgent) && !requestersAdvertisedTimeSlots.isEmpty()) {
                        // Flip the "made interaction" flag
                        householdAgentsInteractions.replace(requesterAgent, true);

                        // Browse the advertised timeslots and try to find a requested timeslot
                        Pair<TimeSlot, AID> timeSlotOwnerPair = findRequestedTimeSlotInAdverts(
                                requestedTimeSlotsHolder.timeSlots(),
                                requesterAgent,
                                agentsToReceiveATradeOffer
                        );

                        // Check if the requester has any timeslots to offer in return and if a desired timeslot was found
                        if (timeSlotOwnerPair != null) {
                            AID receiverAgent = timeSlotOwnerPair.second();

                            // Offer the requester's least wanted timeslot - the first element of the advert
                            // Send the created trade offer object to the requester agent so that it can forward
                            // it to the target agent.
                            AgentHelper.sendMessage(
                                    myAgent,
                                    requesterAgent,
                                    "Offer Created",
                                    new TradeOffer(
                                            requesterAgent,
                                            receiverAgent,
                                            requestersAdvertisedTimeSlots.getFirst(),
                                            timeSlotOwnerPair.first()
                                    ),
                                    ACLMessage.AGREE
                            );

                            numOfTradesStarted++;
                            refuseRequest = false;
                        }
                    }
                } else {
                    AgentHelper.printAgentError(myAgent.getLocalName(), "Inquiry for timeslots cannot be processed: the received object has an incorrect type or is null.");
                }

                numOfRequestsProcessed++;

                // After processing each call for proposal, check if all agents have sent this message
                final int populationCount = config.getPopulationCount();

                // Check if all calls for proposal had been processed
                if (numOfRequestsProcessed == populationCount && agentsToReceiveATradeOffer.size() <= populationCount) {
                    // By subtracting the arraylist of agents from the list of all agents, get the agents who did not
                    // receive a trade request in the current exchange round and notify them.
                    agentsToNotify = new ArrayList<>(getHouseholdAgentAIDList());
                    agentsToNotify.removeAll(agentsToReceiveATradeOffer);

                    // Broadcast the "no offers" message to the agents who did not receive a trade offer for various reasons
                    AgentHelper.sendMessage(
                            myAgent,
                            agentsToNotify,
                            "No Expected Offers This Round",
                            ACLMessage.INFORM
                    );
                }

                if (refuseRequest) {
                    // Reach this block if any of these events happened:
                    // - the requester had no timeslots advertised to offer in return
                    // - the object sent by the requester can't be processed
                    // - the receiver of the request has already made interaction with another agent this round
                    // - none of the desired timeslots were found in the adverts

                    // Send a reply to the requester agent about the request not being fulfilled
                    AgentHelper.sendMessage(
                            myAgent,
                            requesterAgent,
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
            return numOfRequestsProcessed == config.getPopulationCount();
        }

        @Override
        public int onEnd() {
            // At the first exchange of each day, calculate the initial and optimum agent satisfactions
            if (currentExchangeRound == 1) {
                calculateInitialAndOptimumSatisfactions();
            }

            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return 0;
        }
    }

    /**
     * Listens for the outcome of the trades that were started as a result of the inquiries.
     * Specific to the Smart Contract exchange type.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class StartedTradesOutcomeSCListener extends Behaviour {
        private int numOfOutcomesReceived = 0;

        public StartedTradesOutcomeSCListener(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Check if any trades have been started in the current exchange round
            if (numOfTradesStarted > 0) {
                // Listen for the outcome of started trades
                ACLMessage tradeOutcomeMessage = AgentHelper.receiveMessage(myAgent, "Trade Outcome");

                if (tradeOutcomeMessage != null) {
                    // Make sure the incoming object is readable
                    Serializable receivedObject = AgentHelper.readReceivedContentObject(tradeOutcomeMessage, myAgent.getLocalName(), TradeOffer.class, String.class);

                    // Check if the trade was not rejected
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
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done updating adverts based on started trades");
            }

            return 0;
        }
    }

    /**
     * Listens for Household agents being finished with the exchange round on their side.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class ExchangeRoundOverListener extends Behaviour {
        private final HashMap<AID, EndOfExchangeHouseholdDataHolder> dataHolders = new HashMap<>();

        public ExchangeRoundOverListener(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for Household agents that are done for the current exchange round
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

                    // Store the statistical values from the Household agent's exchange round
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
            return dataHolders.size() == config.getPopulationCount();
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(
                        myAgent.getLocalName(),
                        "Exchange round " + currentExchangeRound +  " over." +
                                " | Trades started: " + numOfTradesStarted +
                                " | Successful exchanges: " + numOfSuccessfulExchanges
                );
            }

            /*
            The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
            See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java
            */

            if (numOfSuccessfulExchanges == 0) {
                exchangeTimeout++;
            } else {
                exchangeTimeout = 0;
            }

            // Write exchange data to file
            for (AgentStrategyType agentStrategyType : AgentStrategyType.values()) {
                outputInstance.appendExchangeData(
                        timeTracker.getCurrentSimulationRun(),
                        timeTracker.getCurrentDay(),
                        currentExchangeRound,
                        agentStrategyType,
                        AgentHelper.averageAgentSatisfaction(householdAgentContacts, agentStrategyType)
                );
            }

            // Write performance data to file
            for (AID householdAgent : this.dataHolders.keySet()) {
                AgentStrategyType strategyType = null;

                for (AgentContact householdAgentContact : householdAgentContacts) {
                    if (householdAgentContact.getAgentIdentifier().equals(householdAgent)) {
                        strategyType = householdAgentContact.getType();

                        break;
                    }
                }

                outputInstance.appendPerformanceData(
                        timeTracker.getCurrentSimulationRun(),
                        timeTracker.getCurrentDay(),
                        currentExchangeRound,
                        strategyType,
                        dataHolders.get(householdAgent).isTradeOfferReceiver(),
                        dataHolders.get(householdAgent).exchangeRoundHouseholdCPUTime()
                );
            }

            // Check if there have been 10 exchange rounds without any successful trades
            if (exchangeTimeout == 10) {
                // Create and add the end of day behaviour sequence to the agent's behaviour queue
                SequentialBehaviour endOfDaySequence = new SequentialBehaviour();

                endOfDaySequence.addSubBehaviour(new InitiateSocialLearningBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new SocialLearningOverListenerBehaviour(myAgent));
                endOfDaySequence.addSubBehaviour(new CallItADayBehaviour(myAgent));

                myAgent.addBehaviour(endOfDaySequence);
            } else {
                currentExchangeRound++;

                // Recreate the exchange initiator behavior and add it back to the agent's behaviour queue
                // Determine which exchange type to use based on the current value in the configuration
                switch (config.getExchangeType()) {
                    case MessagePassing -> myAgent.addBehaviour(new InitiateExchangeBehaviour(myAgent));
                    case SmartContract -> myAgent.addBehaviour(new InitiateSCExchangeBehaviour(myAgent));
                }
            }

            return 0;
        }
    }

    /**
     * Starts the social learning activity at the end of a day.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class InitiateSocialLearningBehaviour extends OneShotBehaviour {
        public InitiateSocialLearningBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            /*
            The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
            See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/SocialLearning.java
            */

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

                numOfAgentsSelectedForSocialLearning++;
            }
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "initiated social learning");
            }

            return 0;
        }
    }

    /**
     * Listens for the responses from the Household agents being finished with the social learning activity.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class SocialLearningOverListenerBehaviour extends Behaviour {
        private int socialLearningOverMessagesReceived = 0;

        public SocialLearningOverListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for replies to the social learning initiation
            ACLMessage socialLearningOverMessage = AgentHelper.receiveMessage(myAgent, "Social Learning Done");

            if (socialLearningOverMessage != null) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(socialLearningOverMessage, myAgent.getLocalName(), AgentContact.class);

                // Make sure the incoming object is of the expected type
                if (receivedObject instanceof AgentContact agentContactAfterSocialLearning) {
                    // Update the Household agent contacts following the social learning
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
            return socialLearningOverMessagesReceived == numOfAgentsSelectedForSocialLearning;
        }

        @Override
        public int onEnd() {
            if (config.isDebugMode()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done waiting for social learning to finish");
            }

            return 0;
        }
    }

    /**
     * Listens for Household agents being done with their daily activities and notifying the Ticker agent.
     * A reusable behaviour of AdvertisingBoardAgent.
     */
    public class CallItADayBehaviour extends Behaviour {
        private final HashMap<AgentContact, EndOfDayHouseholdAgentDataHolder> householdAgentsEndOfDayData = new HashMap<>();

        public CallItADayBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            // Listen for Household agents being done with their daily tasks
            ACLMessage householdDoneMessage = AgentHelper.receiveMessage(myAgent, "Done");

            if (householdDoneMessage != null) {
                // Make sure the incoming object is readable
                Serializable receivedObject = AgentHelper.readReceivedContentObject(householdDoneMessage, myAgent.getLocalName(), EndOfDayHouseholdAgentDataHolder.class);

                // Make sure the incoming object is of the expected type
                if (receivedObject instanceof EndOfDayHouseholdAgentDataHolder householdAgentDataHolder) {
                    AgentContact doneHouseholdContact = null;

                    // Update the Household agent contacts at the end of the day
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
            return householdAgentsEndOfDayData.size() == config.getPopulationCount();
        }

        @Override
        public int onEnd() {
            // End the day of the Advertising agent
            double overallRunSatisfactionSum = 0;
            double socialAgentsRunSatisfactionSum = 0;
            int numOfSocialAgents = 0;

            // Calculate the sums of overall and type respective Household agent satisfaction
            for (AgentContact householdAgentContact : householdAgentContacts) {
                if (householdAgentContact.getType() == AgentStrategyType.SOCIAL) {
                    socialAgentsRunSatisfactionSum += householdAgentContact.getCurrentSatisfaction();
                    numOfSocialAgents++;
                }

                overallRunSatisfactionSum += householdAgentContact.getCurrentSatisfaction();
            }

            // Calculate statistical values related to agent satisfaction
            double averageSocialSatisfaction = socialAgentsRunSatisfactionSum / (double)config.getPopulationCount();
            double averageSelfishSatisfaction = (overallRunSatisfactionSum - socialAgentsRunSatisfactionSum) / (double)config.getPopulationCount();
            double averageSocialSatisfactionStandardDeviation = AgentHelper.averageSatisfactionStandardDeviation(householdAgentContacts, AgentStrategyType.SOCIAL, overallRunSatisfactionSum / (double)config.getPopulationCount());
            double averageSelfishSatisfactionStandardDeviation = AgentHelper.averageSatisfactionStandardDeviation(householdAgentContacts, AgentStrategyType.SELFISH, overallRunSatisfactionSum / (double)config.getPopulationCount());
            AgentStatisticalValuesPerStrategyType socialStatisticalValues = new AgentStatisticalValuesPerStrategyType(householdAgentContacts, AgentStrategyType.SOCIAL);
            AgentStatisticalValuesPerStrategyType selfishStatisticalValues = new AgentStatisticalValuesPerStrategyType(householdAgentContacts, AgentStrategyType.SELFISH);

            // Write daily data to file
            outputInstance.appendDailyData(
                    timeTracker.getCurrentSimulationRun(),
                    timeTracker.getCurrentDay(),
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

            // Write agent data to file
            for (AgentContact householdAgentContact : householdAgentContacts) {
                outputInstance.appendAgentData(
                        timeTracker.getCurrentSimulationRun(),
                        timeTracker.getCurrentDay(),
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

            // Create a data holder containing daily satisfaction data
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

            // Notify the Ticker agent that the Advertising agent is done with its daily tasks
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

    /**
     * Sets the initial state of the agent.
     */
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

        this.config = SimulationConfigurationSingleton.getInstance();
        this.timeTracker = TickerTrackerSingleton.getInstance();
        this.outputInstance = DataOutputSingleton.getInstance();
    }

    /**
     * Extracts the list of AID objects from the interaction storage map.
     *
     * @return (ArrayList of Agent Identifiers) A list of all known Household agent contacts.
     */
    private ArrayList<AID> getHouseholdAgentAIDList() {
        return new ArrayList<>(this.householdAgentsInteractions.keySet());
    }

    /**
     * Sets the state of the agent to the same as before the first exchange started.
     */
    private void resetExchange() {
        this.adverts.clear();
        this.numOfTradesStarted = 0;
        this.numOfSuccessfulExchanges = 0;
        this.agentsToNotify.clear();
        this.householdAgentsInteractions.clear();

        // Shuffle the list of household agents before every exchange
        Collections.shuffle(this.householdAgentContacts, config.getRandom());

        // Reset each household agent's "made interaction" flag to false
        // By recreating the hashmap that holds the (AID, Boolean) pairs
        for (AgentContact contact : this.householdAgentContacts) {
            this.householdAgentsInteractions.put(contact.getAgentIdentifier(), false);
        }
    }

    /**
     * Seeks out a one of the requester Household agent's desired timeslots.
     *
     * @param requestedTimeSlots The Household agent's desired timeslots.
     * @param requesterHouseholdAgent The AID of the requester Household agent.
     * @param agentsToReceiveATradeOffer The storage of Household agents who are receiving a trade offer in the current exchange round.
     * @return (Pair(Timeslot, AID)) The pair containing the desired timeslot and its current owner, or null if no desired timeslots were found or the requester has no advertised timeslots of its own.
     */
    private Pair<TimeSlot, AID> findRequestedTimeSlotInAdverts(TimeSlot[] requestedTimeSlots, AID requesterHouseholdAgent, ArrayList<AID> agentsToReceiveATradeOffer) {
        TimeSlot targetTimeSlot = null;
        AID targetReceiver = null;

        /*
        The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
        See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/SocialLearning.java
        */

        ArrayList<AID> shuffledAdvertPosters = new ArrayList<>(adverts.keySet());

        // Remove the requesting agent from the temp advert catalogue to avoid an unnecessary check
        shuffledAdvertPosters.remove(requesterHouseholdAgent);
        Collections.shuffle(shuffledAdvertPosters, config.getRandom());

        // Find a desired timeslot in the published adverts
        browsingTimeSlots:
        for (TimeSlot desiredTimeSlot : requestedTimeSlots) {
            for (AID advertPoster : shuffledAdvertPosters) {
                // Check if the potential receiving household agent has made an interaction in the current exchange round
                if (!this.householdAgentsInteractions.get(advertPoster)) {
                    ArrayList<TimeSlot> timeSlotsForTrade = adverts.get(advertPoster);

                    for (TimeSlot timeSlotForTrade : timeSlotsForTrade) {
                        // Check if the currently analysed timeslot was one of the requested timeslots
                        if (desiredTimeSlot.equals(timeSlotForTrade)) {
                            // Overwrite the input variables
                            targetTimeSlot = timeSlotForTrade;
                            targetReceiver = advertPoster;

                            // Add the target agent to the list of agents to receive a trade offer
                            agentsToReceiveATradeOffer.add(advertPoster);
                            // Flip the target agent's "made interaction" flag to true so that
                            // it does not get paired up with other agents this round
                            this.householdAgentsInteractions.replace(advertPoster, true);

                            break browsingTimeSlots;
                        }
                    }
                }
            }
        }

        if (targetTimeSlot != null) {
            return new Pair<>(targetTimeSlot, targetReceiver);
        } else {
            return null;
        }
    }

    /**
     * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/CalculateSatisfaction.java">ResourceExchangeArena</a>
     */
    private void calculateInitialAndOptimumSatisfactions() {
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