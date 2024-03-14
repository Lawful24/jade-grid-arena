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

public class AdvertisingBoardAgent extends Agent {
    private ArrayList<TimeSlot> availableTimeSlots;
    private HashMap<AID, ArrayList<TimeSlot>> adverts;
    private int numOfTradesStarted;
    private int numOfSuccessfulExchanges;
    private ArrayList<AID> agentsToNotify;
    private int exchangeTimeout;

    // Agent contact attributes
    private AID tickerAgent;
    private ArrayList<AID> householdAgents;

    @Override
    protected void setup() {
        availableTimeSlots = new ArrayList<>();
        adverts = new HashMap<>();
        numOfTradesStarted = 0;
        numOfSuccessfulExchanges = 0;
        agentsToNotify = new ArrayList<>();
        exchangeTimeout = 0;

        householdAgents = new ArrayList<>();

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
                    // Set the daily resources and adverts to their initial values
                    reset();

                    // Define the daily sub-behaviours
                    SequentialBehaviour dailyTasks = new SequentialBehaviour();
                    dailyTasks.addSubBehaviour(new FindHouseholdsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new GenerateTimeSlotsBehaviour(myAgent));
                    dailyTasks.addSubBehaviour(new DistributeInitialRandomTimeSlotAllocations(myAgent));
                    dailyTasks.addSubBehaviour(new InitiateExchangeBehaviour(myAgent));

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

    public class InitiateExchangeBehaviour extends Behaviour {
        private boolean isExchangeActive = false;
        private final SequentialBehaviour exchange = new SequentialBehaviour();

        public InitiateExchangeBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (!isExchangeActive) {
                reset();

                exchange.addSubBehaviour(new NewAdvertListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new InterestListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new TradeOfferResponseListenerBehaviour(myAgent));
                exchange.addSubBehaviour(new SocialCapitaSyncPropagateBehaviour(myAgent));

                myAgent.addBehaviour(exchange);

                isExchangeActive = true;

                AgentHelper.sendMessage(
                        myAgent,
                        householdAgents,
                        "Exchange Initiated",
                        ACLMessage.REQUEST
                );
            }
        }

        @Override
        public boolean done() {
            return exchange.done();
        }

        @Override
        public int onEnd() {
            AgentHelper.printAgentLog(
                    myAgent.getLocalName(),
                    "Exchange round over." +
                            " | Trades started: " + numOfTradesStarted +
                            " | Successful exchanges: " + numOfSuccessfulExchanges
            );

            if (numOfSuccessfulExchanges == 0) {
                exchangeTimeout++;
            } else {
                exchangeTimeout = 0;
            }

            if (exchangeTimeout == 10) {
                myAgent.addBehaviour(new CallItADayBehaviour(myAgent));
            } else {
                myAgent.addBehaviour(new InitiateExchangeBehaviour(myAgent));
            }

            return 0;
        }

        @Override
        public void reset() {
            super.reset();
            adverts.clear();
            numOfTradesStarted = 0;
            numOfSuccessfulExchanges = 0;
            agentsToNotify.clear();
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
                // TODO: make sure this stays true even when some agents have no more unwanted slots
                // TODO: make sure the list of household agents is already populated
                if (numOfAdvertsReceived == RunConfigurationSingleton.getInstance().getPopulationCount()) {
                    // Broadcast to all agents that the exchange is open
                    AgentHelper.sendMessage(
                            myAgent,
                            householdAgents,
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
            if (numOfAdvertsReceived == RunConfigurationSingleton.getInstance().getPopulationCount()) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for new adverts");
            }

            return numOfAdvertsReceived == RunConfigurationSingleton.getInstance().getPopulationCount();
        }
    }

    public class InterestListenerBehaviour extends Behaviour {
        private int numOfRequestsProcessed = 0;
        private final ArrayList<AID> agentsToReceive = new ArrayList<>();

        public InterestListenerBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            boolean refuseRequest = true;
            ACLMessage interestMessage = AgentHelper.receiveMessage(myAgent, ACLMessage.CFP);

            if (interestMessage != null && adverts.size() == RunConfigurationSingleton.getInstance().getPopulationCount()) {
                // Prepare a trade offer to the owner of the desired timeslot if that timeslot is available for trade
                ArrayList<TimeSlot> sendersAdvertisedTimeSlots = new ArrayList<>();

                // Find out if the sender has any timeslots available to trade
                for (AID advertPoster : adverts.keySet()) {
                    if (advertPoster.equals(interestMessage.getSender())) {
                        sendersAdvertisedTimeSlots = adverts.get(advertPoster);

                        break;
                    }
                }

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
                            Collections.shuffle(shuffledAdvertPosters, RunConfigurationSingleton.getInstance().getRandom());

                            // Find the desired timeslot in the published adverts
                            browsingTimeSlots:
                            for (TimeSlot desiredTimeSlot : desiredTimeSlots) {
                                for (AID advertPoster : shuffledAdvertPosters) {
                                    ArrayList<TimeSlot> timeSlotsForTrade = adverts.get(advertPoster);

                                    for (TimeSlot timeSlotForTrade : timeSlotsForTrade) {
                                        if (desiredTimeSlot.equals(timeSlotForTrade)) {
                                            targetTimeSlot = timeSlotForTrade;
                                            targetOwner = advertPoster;
                                            agentsToReceive.add(targetOwner);

                                            break browsingTimeSlots;
                                        }
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

                if (refuseRequest) {
                    // TODO: let the requester receive this
                    // Reach this part if any of these events happened:
                    // - the object sent by the requester can't be processed
                    // - the sender had no timeslots advertised to offer in return
                    // - none of the desired timeslots were found in the adverts
                    AgentHelper.sendMessage(
                            myAgent,
                            interestMessage.getSender(),
                            "Desired Timeslots Not Available",
                            ACLMessage.REFUSE
                    );
                }

                numOfRequestsProcessed++;
                final int populationCount = RunConfigurationSingleton.getInstance().getPopulationCount();

                if (numOfRequestsProcessed == populationCount && agentsToReceive.size() < populationCount) {
                    agentsToNotify = new ArrayList<>(householdAgents);
                    agentsToNotify.removeAll(agentsToReceive);

                    AgentHelper.sendMessage(
                            myAgent,
                            agentsToNotify,
                            "No Offers",
                            ACLMessage.PROPOSE
                    );
                }
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            boolean areAllRequestsProcessed = numOfRequestsProcessed == RunConfigurationSingleton.getInstance().getPopulationCount();

            if (areAllRequestsProcessed) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done processing cfps");
            }

            return areAllRequestsProcessed;
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
                                        tradeOfferResponseMessage.getContent(),
                                        incomingObject,
                                        ACLMessage.AGREE
                                );

                                numOfSuccessfulExchanges++;
                            } else {
                                AgentHelper.sendMessage(
                                        myAgent,
                                        ((TradeOffer) incomingObject).senderAgent(),
                                        "Trade Refused",
                                        ACLMessage.CANCEL
                                );
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
            if (numOfTradesStarted == numOfTradeOfferReplies) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done listening for trades");
            }

            return numOfTradesStarted == numOfTradeOfferReplies;
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
                    if (!incomingSyncMessage.getContent().equals("No Syncing Necessary")) {
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
                                        incomingSyncMessage.getContent(),
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
                        householdAgents,
                        "No Syncing Necessary",
                        ACLMessage.INFORM_IF
                );

                allSyncActionsHandled = true;
            }
        }

        @Override
        public boolean done() {
            if (allSyncActionsHandled) {
                AgentHelper.printAgentLog(myAgent.getLocalName(), "done propagating");
            }

            return allSyncActionsHandled;
        }
    }

    public class CallItADayBehaviour extends OneShotBehaviour {

        public CallItADayBehaviour(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            AgentHelper.sendMessage(myAgent, tickerAgent, "Done", ACLMessage.INFORM);

            myAgent.removeBehaviour(this);
        }
    }
}
