package com.napier;

import com.napier.concepts.AgentContact;
import com.napier.concepts.TimeSlot;
import com.napier.concepts.TimeSlotSatisfactionPair;
import com.napier.singletons.SimulationConfigurationSingleton;
import com.napier.types.AgentStrategyType;
import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * @author László Tárkányi
 */
public class AgentHelper {
    /**
     * Registers an agent with the JADE Directory Facilitator.
     *
     * @param agentToRegister The agent object to be registered.
     * @param agentClassName The canonical name of the agent class.
     */
    public static void registerAgent(Agent agentToRegister, String agentClassName) {
        // TODO: Cite JADE workbook or JADE documentation
        // Create a Directory Facilitator Description with the AID of the agent
        DFAgentDescription dfAgentDescription = new DFAgentDescription();
        dfAgentDescription.setName(agentToRegister.getAID());

        // Describe the type of the agent
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(agentClassName);
        serviceDescription.setName(agentToRegister.getLocalName() + "-" + agentClassName + "-agent");
        dfAgentDescription.addServices(serviceDescription);

        // Register agent in the Directory Facilitator
        try {
            DFService.register(agentToRegister, dfAgentDescription);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deregisters an agent from the JADE Directory Facilitator.
     *
     * @param agentToDeregister The agent object to deregister.
     */
    public static void deregisterAgent(Agent agentToDeregister) {
        // TODO: Cite JADE workbook
        // Deregister agent from the Directory Facilitator
        try {
            DFService.deregister(agentToDeregister);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves all agents of a given type from the Directory Facilitator to its own contact directory ("phone book").
     *
     * @see <a href="https://jade.tilab.com/documentation/examples/yellow-pages/">JADE Yellow Pages</a>
     *
     * @param agent The agent that is required to communicate with other agents.
     * @param agentTypeToFind The type of agent to be saved.
     * @return (ArrayList of AgentContacts) A list of the agents saved in a custom AgentContact wrapper format.
     */
    public static ArrayList<AgentContact> saveAgentContacts(Agent agent, String agentTypeToFind) {
        // TODO: Cite JADE workbook
        ArrayList<AgentContact> agentContacts = new ArrayList<>();

        DFAgentDescription agentDescription = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(agentTypeToFind);
        agentDescription.addServices(serviceDescription);

        try {
            DFAgentDescription[] agentsOfType = DFService.search(agent, agentDescription);

            for (DFAgentDescription foundAgent : agentsOfType) {
                String nickname = foundAgent.getName().getLocalName();

                if (nickname.contains("Household") && !foundAgent.getName().equals(agent.getAID())) {
                    agentContacts.add(new AgentContact(foundAgent.getName(), determineAgentType(nickname)));
                } else {
                    agentContacts.add(new AgentContact(foundAgent.getName()));
                }
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        return agentContacts;
    }

    /**
     * Sends a FIPA compliant message from one agent to another.
     *
     * @see <a href="https://jmvidal.cse.sc.edu/talks/agentcommunication/performatives.html">FIPA Performatives</a>
     *
     * @param sender The agent to send the message from.
     * @param receiver The agent to receive the message.
     * @param messageText The text content of the message.
     * @param performative The FIPA performative of the message.
     */
    public static void sendMessage(Agent sender, AID receiver, String messageText, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidFIPAPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setConversationId(messageText);

            // Assign the receiver
            message.addReceiver(receiver);

            // Send the message
            sender.send(message);
        }
    }

    /**
     * Broadcasts a FIPA compliant message from one agent to multiple agents.
     *
     * @see <a href="https://jmvidal.cse.sc.edu/talks/agentcommunication/performatives.html">FIPA Performatives</a>
     *
     * @param sender The agent to send the message from.
     * @param receivers The agents to receive the message.
     * @param messageText The text content of the message.
     * @param performative The FIPA performative of the message.
     */
    public static void sendMessage(Agent sender, ArrayList<AID> receivers, String messageText, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidFIPAPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setConversationId(messageText);

            // Assign the receivers
            for (AID receiver : receivers) {
                message.addReceiver(receiver);
            }

            // Send the message
            sender.send(message);
        }
    }

    /**
     * Sends a message from one agent to another that contains a serialized object.
     *
     * @see <a href="https://jmvidal.cse.sc.edu/talks/agentcommunication/performatives.html">FIPA Performatives</a>
     *
     * @param sender The agent to send the message from.
     * @param receiver The agent to receive the message.
     * @param messageText The text content of the message.
     * @param object The serializable object to send.
     * @param performative The FIPA performative of the message.
     */
    public static void sendMessage (Agent sender, AID receiver, String messageText, Serializable object, int performative) {
        if (object != null) {
            // Check if provided int is a registered ACL performative
            if (isValidFIPAPerformative(performative)) {
                // Build the message
                ACLMessage message = new ACLMessage(performative);

                message.setConversationId(messageText);

                try {
                    message.setContentObject(object);
                } catch (IOException e) {
                    AgentHelper.printAgentError(
                            sender.getLocalName(),
                            "Failed to send object to "
                                    + receiver.getLocalName() + ": "
                                    + e.getMessage() + "\n"
                                    + "Sending empty message."
                    );
                }

                // Assign the receiver
                message.addReceiver(receiver);

                // Send the message
                sender.send(message);
            }
        } else {
            printAgentError(sender.getLocalName(), "Cannot send an ACLMessage with null as its content.");
        }
    }

    /**
     * Attempts to receive a FIPA compliant message with a given specific message text.
     *
     * @param agentToReceive The agent to receive the message.
     * @param messageText The conversation ID of the incoming message. In this project, the conversationId of an ACL message serves as the text content as the content gets overwritten by contentObjects.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageText) {
        return agentToReceive.receive(MessageTemplate.MatchConversationId(messageText));
    }

    /**
     * Attempts to receive a FIPA compliant message by 2 possible matching text contents.
     *
     * @param agentToReceive The agent to receive the message.
     * @param messageText The conversationId of the incoming message. In this project, the conversationId of an ACL message serves as the text content as the content gets overwritten by contentObjects.
     * @param optionalMessageText The optional conversationId of the incoming message.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageText, String optionalMessageText) {
        return agentToReceive.receive(MessageTemplate.or(MessageTemplate.MatchConversationId(messageText), MessageTemplate.MatchConversationId(optionalMessageText)));
    }

    /**
     * Attempts to receive a FIPA compliant message with a given specific message text.
     *
     * @param agentToReceive The agent to receive the message.
     * @param sender The agent to receive the message from.
     * @param messageText The conversation ID of the incoming message. In this project, the conversationId of an ACL message serves as the text content as the content gets overwritten by contentObjects.
     * @param performative The FIPA performative of the incoming message, serving as the label.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveMessage(Agent agentToReceive, AID sender, String messageText, int performative) {
        // Check if the provided performative is a valid FIPA performative
        if (isValidFIPAPerformative(performative)) {
            return agentToReceive.receive(
                    MessageTemplate.and(
                            MessageTemplate.MatchSender(sender),
                            MessageTemplate.and(
                                    MessageTemplate.MatchConversationId(messageText),
                                    MessageTemplate.MatchPerformative(performative)
                            )
                    )
            );
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

    /**
     * Attempts to receive a FIPA compliant message with a given specific message text.
     *
     * @param agentToReceive The agent to receive the message.
     * @param performative The FIPA performative of the incoming message, serving as the label.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveMessage(Agent agentToReceive, int performative) {
        // Check if the provided performative is a valid FIPA performative
        if (isValidFIPAPerformative(performative)) {
            return agentToReceive.receive(MessageTemplate.MatchPerformative(performative));
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

    /**
     * Attempts to receive a FIPA compliant message with a given specific message text.
     *
     * @param agentToReceive The agent to receive the message.
     * @param sender The agent to receive the message from.
     * @param performative The FIPA performative of the incoming message, serving as the label.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveMessage(Agent agentToReceive, AID sender, int performative) {
        // Check if the provided performative is a valid FIPA performative
        if (isValidFIPAPerformative(performative)) {
            return agentToReceive.receive(MessageTemplate.and(MessageTemplate.MatchSender(sender), MessageTemplate.MatchPerformative(performative)));
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

    /**
     * Attempts to receive a reply to a previous Call For Proposal message.
     *
     * @param agentToReceive The agent to receive the message.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveCFPReply(Agent agentToReceive) {
        // Let the agent receive either AGREE, CANCEL, or REFUSE messages
        return agentToReceive.receive(
                MessageTemplate.or(
                        MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                        MessageTemplate.or(
                                MessageTemplate.MatchPerformative(ACLMessage.AGREE),
                                MessageTemplate.MatchPerformative(ACLMessage.CANCEL)
                        )
                )
        );
    }

    /**
     * Attempts to receive a reply to a previous Proposal message.
     *
     * @param agentToReceive The agent to receive the message.
     * @return (ACLMessage or null) An ACLMessage object containing information enclosed by the sender or null if no message with the given parameters has been received yet.
     */
    public static ACLMessage receiveProposalReply(Agent agentToReceive) {
        // Let the agent receive either ACCEPT_PROPOSAL, REJECT_PROPOSAL, or REFUSE messages
        return agentToReceive.receive(
                MessageTemplate.or(
                        MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                        MessageTemplate.or(
                                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                                MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
                        )
                )
        );
    }

    /**
     * Writes output to the console with the name of an agent.
     * Helps to identify what each individual agent does.
     *
     * @param agentNickname The agent to print the log.
     * @param logMessage The text content of the log.
     */
    public static void printAgentLog(String agentNickname, String logMessage) {
        System.out.println(agentNickname + " says: " + logMessage);
    }

    /**
     * Writes output to the console with the name of an agent.
     * Helps to identify the errors of an individual agent.
     *
     * @param agentNickname The agent to print the error log.
     * @param errorMessage The text content of the error log.
     */
    public static void printAgentError(String agentNickname, String errorMessage) {
        System.err.println(agentNickname + " error: " + errorMessage);
    }

    /**
     * Deserialize a contentObject received in a message.
     *
     * @param receivedMessage The message containing the object to be deserialized.
     * @param messageReceiverNickname The localName of the agent that received the message.
     * @param expectedClass The canonical name of the object expected to be enclosed in the received message.
     * @return (Serializable or null) The object enclosed in the message or null if the contentObject of the received message object is not readable.
     */
    public static Serializable readReceivedContentObject(ACLMessage receivedMessage, String messageReceiverNickname, Class<?> expectedClass) {
        Serializable receivedObject = null;

        try {
            receivedObject = receivedMessage.getContentObject();
        } catch (UnreadableException e) {
            AgentHelper.printAgentError(messageReceiverNickname, "Incoming expected " + expectedClass.getCanonicalName() + " object is unreadable:");
            e.printStackTrace();
        }

        return receivedObject;
    }

    /**
     * Deserialize a contentObject received in a message.
     *
     * @param receivedMessage The message containing the object to be deserialized.
     * @param messageReceiverNickname The localName of the agent that received the message.
     * @param expectedClass The canonical name of the class of the object expected to be enclosed in the received message.
     * @param optionalExpectedClass The optional canonical name of the class of the object expected to be enclosed in the received message.
     * @return (Serializable or null) The object enclosed in the message or null if the contentObject of the received message object is not readable.
     */
    public static Serializable readReceivedContentObject(ACLMessage receivedMessage, String messageReceiverNickname, Class<?> expectedClass, Class<?> optionalExpectedClass) {
        Serializable receivedObject = null;

        try {
            receivedObject = receivedMessage.getContentObject();
        } catch (UnreadableException e) {
            AgentHelper.printAgentError(messageReceiverNickname, "Incoming expected " + expectedClass.getCanonicalName() + " or " + optionalExpectedClass.getCanonicalName() + " object is unreadable:");
            e.printStackTrace();
        }

        return receivedObject;
    }

    /**
     * Provides a strategy type based on the Household agent's nickname.
     *
     * @param householdNickname The localName of a Household agent.
     * @return (AgentStrategyType or null) The enum value based on the agent's nickname or null if the Household agent's number is too high or if a non-Household agent is provided.
     */
    public static AgentStrategyType determineAgentType(String householdNickname) {
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();
        AgentStrategyType agentType;

        // Check if only one agent type is supposed to be used
        if (config.doesUtiliseSingleAgentType()) {
            agentType = config.getSelectedSingleAgentType();
        } else {
            // Set the agent type to a specific value based on the ratio provided in the configuration
            // Check if the agent falls into the selfish or social group of the population
            // e.g. with a population of 6 agents and a 2:1 selfish:social ratio, the number of selfish agents would be 4
            // Therefore, agents 1-4 are selfish and 5-6 are social.
            if (getHouseholdAgentNumber(householdNickname) <= config.getSelfishPopulationCount()) {
                agentType = AgentStrategyType.SELFISH;
            } else {
                agentType = AgentStrategyType.SOCIAL;
            }
        }

        return agentType;
    }

    /**
     * Finds the Household number in the agent's nickname.
     *
     * @param agentNickname The localName of the Household agent.
     * @return (int) The number assigned to the provided Household agent.
     */
    private static int getHouseholdAgentNumber(String agentNickname) {
        return Integer.parseInt(agentNickname.substring(agentNickname.length() - 1));
    }

    /**
     * Checks if a given integer is a FIPA performative.
     *
     * @param performative The integer representation of a FIPA performative enum.
     * @return (boolean) If the provided performative is a valid FIPA performative.
     */
    private static boolean isValidFIPAPerformative(int performative) {
        return Arrays.asList(ACLMessage.getAllPerformativeNames()).contains(ACLMessage.getPerformative(performative));
    }

    // TODO: Cite Arena code
    /**
     * Calculates the Agents satisfaction with a given list of time-slots by comparing the list with the time-slots
     * requested by this Agent.
     *
     * @param timeSlotsToConsider The set of time-slots to consider.
     * @param requestedTimeSlots  The set of time-slots requested.
     * @return Double The Agents satisfaction with the time-slots given.
     */
    public static double calculateSatisfaction(ArrayList<TimeSlot> timeSlotsToConsider, ArrayList<TimeSlot> requestedTimeSlots) {
        double[] satisfactionCurve = SimulationConfigurationSingleton.getInstance().getSatisfactionCurve();
        ArrayList<TimeSlot> tempRequestedTimeSlots = new ArrayList<>(requestedTimeSlots);
        ArrayList<TimeSlot> nonRequestedTimeSlots = new ArrayList<>();

        // Count the number of the given time-slots that match the Agents requested time-slots.
        double satisfaction = 0;

        for (TimeSlot timeSlot : timeSlotsToConsider) {
            if (tempRequestedTimeSlots.contains(timeSlot)) {
                tempRequestedTimeSlots.remove(timeSlot);
                satisfaction++;
            } else {
                nonRequestedTimeSlots.add(timeSlot);
            }
        }

        List<TimeSlotSatisfactionPair> tempTimeSlotSatisfactions = calculateSatisfactionPerSlot(tempRequestedTimeSlots);

        for (int i = 1; i < satisfactionCurve.length; i++) {
            for (TimeSlot timeSlot: nonRequestedTimeSlots) {
                for (TimeSlotSatisfactionPair pair: tempTimeSlotSatisfactions) {
                    if (pair.getTimeSlot() == timeSlot) {
                        if (pair.getSatisfaction() == satisfactionCurve[i]) {
                            satisfaction += pair.getSatisfaction();
                            TimeSlot timeSlotOver = new TimeSlot(timeSlot.getStartHour() + i);
                            TimeSlot timeSlotUnder = new TimeSlot(timeSlot.getStartHour() - i);

                            if (tempRequestedTimeSlots.contains(timeSlotOver)) {
                                tempRequestedTimeSlots.remove(timeSlotOver);
                                tempTimeSlotSatisfactions = calculateSatisfactionPerSlot(tempRequestedTimeSlots);
                            } else if (tempRequestedTimeSlots.contains(timeSlotUnder)) {
                                tempRequestedTimeSlots.remove(timeSlotUnder);
                                tempTimeSlotSatisfactions = calculateSatisfactionPerSlot(tempRequestedTimeSlots);
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Return the Agents satisfaction with the given time-slots, between 1 and 0.
        return satisfaction / SimulationConfigurationSingleton.getInstance().getNumOfSlotsPerAgent();
    }

    // TODO: Cite Arena code
    /**
     * Takes all Agents individual satisfactions and calculates the average satisfaction of all Agents in the
     * simulation.
     *
     * @param householdAgentContacts Array List of all the Household agents that exist in the current simulation.
     * @return Double Returns the average satisfaction between 0 and 1 of all agents in the simulation.
     */
    public static double calculateCurrentAverageAgentSatisfaction(ArrayList<AgentContact> householdAgentContacts) {
        ArrayList<Double> agentSatisfactions = new ArrayList<>();

        for (AgentContact householdAgentContact : householdAgentContacts) {
            agentSatisfactions.add(householdAgentContact.getCurrentSatisfaction());
        }

        return agentSatisfactions.stream().mapToDouble(val -> val).average().orElse(0.0);
    }

    // TODO: Cite Arena code
    /**
     * Takes all Agents of a given types individual satisfactions and calculates the average satisfaction of the Agents
     * of that type.
     *
     * @param householdAgentContacts Array List of all the agents that exist in the current simulation.
     * @param agentStrategyType The type for which to calculate the average satisfaction of all Agents of that type.
     * @return Double Returns the average satisfaction between 0 and 1 of all agents of the given type.
     */
    public static double averageAgentSatisfaction(ArrayList<AgentContact> householdAgentContacts, AgentStrategyType agentStrategyType) {
        ArrayList<Double> agentSatisfactions = new ArrayList<>();

        for (AgentContact householdAgentContact : householdAgentContacts) {
            if (householdAgentContact.getType() == agentStrategyType) {
                agentSatisfactions.add(householdAgentContact.getCurrentSatisfaction());
            }
        }

        return agentSatisfactions.stream().mapToDouble(val -> val).average().orElse(0.0);
    }

    // TODO: Cite Arena code
    /**
     * Returns the optimum average satisfaction possible for all agents given the current requests and allocations in
     * the simulation.
     *
     * @param allAllocatedTimeSlots The timeslots that were distributed at the start of the day.
     * @param allRequestedTimeSlots All timeslots that were requested by all Household agents at the start of the day.
     *
     * @return Double Returns the highest possible average satisfaction between 0 and 1 of all agents in the simulation.
     */
    public static double calculateOptimumPossibleSatisfaction(ArrayList<TimeSlot> allAllocatedTimeSlots, ArrayList<TimeSlot> allRequestedTimeSlots) {
        // Stores the number of slots that could potentially be fulfilled with perfect trading.
        double satisfiedSlots = 0;

        // Stores the total number of slots requested by all Agents.
        double totalSlots = allRequestedTimeSlots.size();

        for (TimeSlot timeSlot : allRequestedTimeSlots) {
            if (allAllocatedTimeSlots.contains(timeSlot)) {
                // For each request, if it has been allocated to any agent, increase the number of satisfied slots.
                satisfiedSlots++;

                // Remove the slot from the list of all allocated slots so no slots can be allocated twice.
                allAllocatedTimeSlots.remove(timeSlot);
            }
        }

        return satisfiedSlots / totalSlots;
    }

    // TODO: Cite Arena code
    public static ArrayList<TimeSlotSatisfactionPair> calculateSatisfactionPerSlot(ArrayList<TimeSlot> requestedTimeSlots) {
        ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs = new ArrayList<>();
        double[] satisfactionCurve = SimulationConfigurationSingleton.getInstance().getSatisfactionCurve();

        // Calculate the potential satisfaction that each time-slot could give based on their proximity to requested time-slots.
        Double[] slotSatisfaction = new Double[SimulationConfigurationSingleton.getInstance().getNumOfUniqueTimeSlots()];
        Arrays.fill(slotSatisfaction, 0.0);

        for (TimeSlot requestedTimeSlot : requestedTimeSlots) {
            int slotSatisfactionIndex = requestedTimeSlot.getStartHour() - 1;
            slotSatisfaction[slotSatisfactionIndex] = satisfactionCurve[0];

            // Apply the adjustment values to neighboring elements
            for (int i = 1; i < satisfactionCurve.length; i++) {
                int leftIndex = slotSatisfactionIndex - i;
                int rightIndex = slotSatisfactionIndex + i;

                if (leftIndex < 0) {leftIndex += slotSatisfaction.length;}
                if (rightIndex >= slotSatisfaction.length) {rightIndex -= slotSatisfaction.length;}

                slotSatisfaction[leftIndex] = Math.max(slotSatisfaction[leftIndex], satisfactionCurve[i]);
                slotSatisfaction[rightIndex] = Math.max(slotSatisfaction[rightIndex], satisfactionCurve[i]);
            }
        }

        for (int i = 0; i < slotSatisfaction.length; i++) {
            timeSlotSatisfactionPairs.add(new TimeSlotSatisfactionPair(new TimeSlot(i + 1), slotSatisfaction[i]));
        }

        return timeSlotSatisfactionPairs;
    }

    // TODO: Cite Arena code
    /**
     * Takes all Agents of a given types individual satisfactions and calculates the variance between the average
     * satisfaction of the Agents of that type.
     *
     * @param agentContacts Array List of all the agents that exist in the current simulation.
     * @param agentType The type for which to calculate the variance between the average satisfactions of all Agents of
     *                  that type.
     * @param averageOverallSatisfaction The average satisfaction of all Household agents.
     * @return Double Returns the variance between the average satisfactions of all agents of the given type.
     */
    public static double averageSatisfactionStandardDeviation(ArrayList<AgentContact> agentContacts, AgentStrategyType agentType, double averageOverallSatisfaction) {
        double sumDiffsSquared = 0.0;
        int groupSize = 0;

        for (AgentContact agentContact : agentContacts) {
            if (agentContact.getType() == agentType) {
                double diff = agentContact.getCurrentSatisfaction() - averageOverallSatisfaction;
                diff *= diff;
                sumDiffsSquared += diff;
                groupSize++;
            }
        }

        if (groupSize == 0) {
            return 0.0;
        }

        return sqrt(sumDiffsSquared / (double)(groupSize));
    }
}