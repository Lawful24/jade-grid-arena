package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Arrays;

public class AgentHelper {
    // TODO: Cite JADE workbook or JADE documentation
    public static void registerAgent(Agent a, String agentType) {
        // Create a Directory Facilitator Description with the AID of the agent
        DFAgentDescription dfAgentDescription = new DFAgentDescription();
        dfAgentDescription.setName(a.getAID());

        // Describe the type of the agent
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(agentType);
        serviceDescription.setName(a.getLocalName() + "-" + agentType + "-agent");
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
                AgentHelper.logActivity(agent.getLocalName(), "Registered: " + foundAgent.getName());
            }
        } catch (FIPAException e) {
            System.err.println(e.toString());
        }

        return agentContacts;
    }

    public static void sendMessage(Agent sender, AID receiver, String content, int performative) {
        // Check if provided int is a registered ACL performative
        if (Arrays.asList(ACLMessage.getAllPerformativeNames()).contains(ACLMessage.getPerformative(performative))) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setContent(content);

            // Assign the receiver
            message.addReceiver(receiver);

            // Send the message
            sender.send(message);
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
        }
    }

    public static void sendMessage(Agent sender, ArrayList<AID> receivers, String content, int performative) {
        // Check if provided int is a registered ACL performative
        if (Arrays.asList(ACLMessage.getAllPerformativeNames()).contains(ACLMessage.getPerformative(performative))) {
            // Build the message
            ACLMessage message = new ACLMessage(performative);
            message.setContent(content);

            // Assign the receivers
            for (AID receiver : receivers) {
                message.addReceiver(receiver);
            }

            // Send the message
            sender.send(message);
        } else {
            System.err.println("Incorrect ACL performative: " + performative);
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

    public static void logActivity(String agentNickname, String logMessage) {
        System.out.println(agentNickname + " says: " + logMessage);
    }
}
