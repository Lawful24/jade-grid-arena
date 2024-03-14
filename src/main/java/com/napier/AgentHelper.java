package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    public static ArrayList<AID> saveAgentContacts(Agent agent, String agentTypeToFind) {
        ArrayList<AID> agentContacts = new ArrayList<>();

        DFAgentDescription agentDescription = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(agentTypeToFind);
        agentDescription.addServices(serviceDescription);

        try {
            DFAgentDescription[] agentsOfType = DFService.search(agent, agentDescription);

            for (DFAgentDescription foundAgent : agentsOfType) {
                agentContacts.add(foundAgent.getName());
                //AgentHelper.printAgentLog(agent.getLocalName(), "Registered: " + foundAgent.getName());
            }
        } catch (FIPAException e) {
            System.err.println(e.toString());
        }

        return agentContacts;
    }

    public static void sendMessage(Agent sender, AID receiver, String content, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidACLPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setContent(content);

            // Assign the receiver
            message.addReceiver(receiver);

            // Send the message
            sender.send(message);
        }
    }

    public static void sendMessage(Agent sender, ArrayList<AID> receivers, String content, int performative) {
        // Check if provided int is a registered ACL performative
        if (isValidACLPerformative(performative)) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setContent(content);

            // Assign the receivers
            for (AID receiver : receivers) {
                message.addReceiver(receiver);
            }

            // Send the message
            sender.send(message);
        }
    }

    public static void sendMessage (Agent sender, AID receiver, String content, Serializable object, int performative) {
        if (object != null) {
            // Check if provided int is a registered ACL performative
            if (isValidACLPerformative(performative)) {
                // Build the message
                ACLMessage message = new ACLMessage(performative);

                message.setContent(content);

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
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageContent) {
        return agentToReceive.receive(MessageTemplate.MatchContent(messageContent));
    }

    // TODO: Cite JADE workbook
    // TODO: rework this
    public static ACLMessage receiveMessage(Agent agentToReceive, String messageContent, String optionalMessageContent) {
        return agentToReceive.receive(MessageTemplate.or(MessageTemplate.MatchContent(messageContent), MessageTemplate.MatchContent(optionalMessageContent)));
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

    public static AgentStrategyType determineAgentType(int householdAgentNumber) {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
        AgentStrategyType agentType;

        // Check if only one agent type is supposed to be used
        if (config.isSingleAgentTypeUsed()) {
            agentType = config.getSelectedSingleAgentType();
        } else {
            // Set the agent type to a specific value based on the ratio provided in the configuration
            // Check if the agent falls into the selfish or social group of the population
            // e.g. with a population of 6 agents and a 2:1 selfish:social ratio, the number of selfish agents would be 4
            // Therefore, agents 1-4 are selfish and 5-6 are social.
            if (householdAgentNumber <= config.getSelfishPopulationCount()) {
                agentType = AgentStrategyType.SELFISH;
            } else {
                agentType = AgentStrategyType.SOCIAL;
            }
        }

        return agentType;
    }

    public static int getHouseholdAgentNumber(String agentNickname) {
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
        double[] satisfactionCurve = RunConfigurationSingleton.getInstance().getSatisfactionCurve();
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
                    if (pair.timeSlot == timeSlot) {
                        if (pair.satisfaction == satisfactionCurve[i]) {
                            satisfaction += pair.satisfaction;
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
        return satisfaction / RunConfigurationSingleton.getInstance().getNumOfSlotsPerAgent();
    }

    // TODO: Cite Arena code
    public static ArrayList<TimeSlotSatisfactionPair> calculateSatisfactionPerSlot(ArrayList<TimeSlot> requestedTimeSlots) {
        ArrayList<TimeSlotSatisfactionPair> timeSlotSatisfactionPairs = new ArrayList<>();
        double[] satisfactionCurve = RunConfigurationSingleton.getInstance().getSatisfactionCurve();

        // Calculate the potential satisfaction that each time-slot could give based on their proximity to requested time-slots.
        Double[] slotSatisfaction = new Double[RunConfigurationSingleton.getInstance().getNumOfUniqueTimeSlots()];
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
}
