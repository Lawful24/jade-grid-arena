package com.napier;

import jade.core.Agent;

public class HouseholdAgent extends Agent {
    @Override
    protected void setup() {
        AgentAction.registerAgent(this, "household");
    }

    @Override
    protected void takeDown() {
        AgentAction.deregisterAgent(this);
    }
}
