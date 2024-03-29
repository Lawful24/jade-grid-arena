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

public class AgentHelper {
    // TODO: Cite JADE workbook or JADE documentation
    public static void registerAgent(Agent a, String agentClass) {
        // Create a Directory Facilitator Description with the AID of the agent
        DFAgentDescription dfAgentDescription = new DFAgentDescription();
        dfAgentDescription.setName(a.getAID());

        // Describe the type of the agent
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(agentClass);
        serviceDescription.setName(a.getLocalName() + "-" + agentClass + "-agent");
        dfAgentDescription.addServices(serviceDescription);

        // Register agent in the Directory Facilitator
        try {
            DFService.register(a, dfAgentDescription);
        } catch (FIPAException e) {
            System.err.println(e.toString());
        }
    }

    // TODO: Cite JADE workbook
    public static void deregisterAgent(Agent a) {
        // Deregister agent from the Directory Facilitator
        try {
            DFService.deregister(a);
        } catch (FIPAException e) {
            System.err.println(e.toString());
        }
    }

    // TODO: Cite JADE workbook
    public static ArrayList<AgentContact> saveAgentContacts(Agent agent, String agentTypeToFind) {
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
            System.err.println(e.toString());
        }

        return agentContacts;
    }

    public static void sendMessage(Agent sender, AID receiver, String messageText, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidACLPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setConversationId(messageText);

            // Assign the receiver
            message.addReceiver(receiver);

            // Send the message
            sender.send(message);
        }
    }

    public static ACLMessage sendMessage(Agent sender, ArrayList<AID> receivers, String messageText, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidACLPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setConversationId(messageText);

            // Assign the receivers
            for (AID receiver : receivers) {
                message.addReceiver(receiver);
            }

            // Send the message
            sender.send(message);

            return message;
        }

        return null;
    }

    public static void sendMessage (Agent sender, AID receiver, String messageText, Serializable object, int performative) {
        if (object != null) {
            // Check if provided int is a registered ACL performative
            if (isValidACLPerformative(performative)) {
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

    // TODO: Cite JADE workbook
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageText) {
        return agentToReceive.receive(MessageTemplate.MatchConversationId(messageText));
    }

    // TODO: Cite JADE workbook
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageText, String optionalMessageText) {
        return agentToReceive.receive(MessageTemplate.or(MessageTemplate.MatchConversationId(messageText), MessageTemplate.MatchConversationId(optionalMessageText)));
    }

    public static ACLMessage receiveMessage(Agent agentToReceive, AID sender, String messageText, int performative) {
        if (isValidACLPerformative(performative)) {
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

    public static ACLMessage receiveMessage(Agent agentToReceive, int performative) {
        if (isValidACLPerformative(performative)) {
            return agentToReceive.receive(MessageTemplate.MatchPerformative(performative));
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

    public static ACLMessage receiveMessage(Agent agentToReceive, AID sender, int performative) {
        if (isValidACLPerformative(performative)) {
            return agentToReceive.receive(MessageTemplate.and(MessageTemplate.MatchSender(sender), MessageTemplate.MatchPerformative(performative)));
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

    public static ACLMessage receiveMessage(Agent agentToReceive, int performative, int optionalPerformative) {
        if (isValidACLPerformative(performative)) {
            return agentToReceive.receive(MessageTemplate.or(MessageTemplate.MatchPerformative(performative), MessageTemplate.MatchPerformative(optionalPerformative)));
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
            return null;
        }
    }

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

    public static void printAgentLog(String agentNickname, String logMessage) {
        System.out.println(agentNickname + " says: " + logMessage);
    }

    public static void printAgentError(String agentNickname, String errorMessage) {
        System.err.println(agentNickname + " error: " + errorMessage);
    }

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

    private static int getHouseholdAgentNumber(String agentNickname) {
        return Integer.parseInt(agentNickname.substring(agentNickname.length() - 1));
    }

    private static boolean isValidACLPerformative(int performative) {
        return Arrays.asList(ACLMessage.getAllPerformativeNames()).contains(ACLMessage.getPerformative(performative));
    }

    // TODO: Cite Arena code
    /**
     * Calculates the Agents satisfaction with a given list of time-slots by comparing the list with the time-slots
     * requested by this Agent.
     *
     * @param timeSlotsToConsider The set of time-slots to consider.
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
     * @param householdAgentContacts Array List of all the agents that exist in the current simulation.
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
     * @param householdAgentContacts Array List of all the agents that exist in the current simulation.
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
