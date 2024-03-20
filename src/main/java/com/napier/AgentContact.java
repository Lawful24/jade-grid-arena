package com.napier;

import jade.core.AID;

import java.io.Serializable;

public class AgentContact implements Serializable {
    private final AID agentIdentifier;
    private AgentStrategyType type;
    private int currentSatisfaction;

    public AgentContact(AID agentIdentifier) {
        this.agentIdentifier = agentIdentifier;
    }

    public AgentContact(AID agentIdentifier, AgentStrategyType type) {
        this.agentIdentifier = agentIdentifier;
        this.type = type;
        // TODO: satisfaction
    }

    public AID getAgentIdentifier() {
        return agentIdentifier;
    }

    public AgentStrategyType getType() {
        return type;
    }

    public int getCurrentSatisfaction() {
        return currentSatisfaction;
    }

    public void setType(AgentStrategyType type) {
        this.type = type;
    }

    public void setCurrentSatisfaction(int currentSatisfaction) {
        this.currentSatisfaction = currentSatisfaction;
    }
}
