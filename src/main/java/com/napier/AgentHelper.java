package com.napier;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.ArrayList;

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
                System.out.println("Added: " + foundAgent.getName());
            }
        } catch (FIPAException e) {
            System.err.println(e.toString());
        }

        return agentContacts;
    }
}
