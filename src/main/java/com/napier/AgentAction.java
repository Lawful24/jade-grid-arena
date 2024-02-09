package com.napier;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class AgentAction {
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
            e.printStackTrace();
        }
    }

    // TODO: Cite JADE workbook
    public static void deregisterAgent(Agent a) {
        // Deregister agent from the Directory Facilitator
        try {
            DFService.deregister(a);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }
}
