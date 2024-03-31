package com.napier.concepts;

import com.napier.types.AgentStrategyType;
import jade.core.AID;

import java.io.Serializable;
import java.util.Objects;

/**
 * Contains the contact information of a Household agent.
 *
 * @author László Tárkányi
 */
public class AgentContact implements Serializable {
    private final AID agentIdentifier;
    private AgentStrategyType type;
    private double currentSatisfaction;

    /* Constructors */

    public AgentContact(AID agentIdentifier) {
        this.agentIdentifier = agentIdentifier;
    }

    public AgentContact(AID agentIdentifier, AgentStrategyType type) {
        this.agentIdentifier = agentIdentifier;
        this.type = type;
    }

    public AgentContact(AID agentIdentifier, AgentStrategyType type, double currentSatisfaction) {
        this.agentIdentifier = agentIdentifier;
        this.type = type;
        this.currentSatisfaction = currentSatisfaction;
    }

    /* Accessors */

    public AID getAgentIdentifier() {
        return agentIdentifier;
    }

    public AgentStrategyType getType() {
        return type;
    }

    public double getCurrentSatisfaction() {
        return currentSatisfaction;
    }

    /* Mutators */

    public void setType(AgentStrategyType type) {
        this.type = type;
    }

    public void setCurrentSatisfaction(double currentSatisfaction) {
        this.currentSatisfaction = currentSatisfaction;
    }


    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        AgentContact that = (AgentContact) object;

        return Objects.equals(agentIdentifier, that.agentIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentIdentifier);
    }
}