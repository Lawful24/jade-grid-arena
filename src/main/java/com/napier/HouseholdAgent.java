package com.napier;

import jade.core.Agent;

public class HouseholdAgent extends Agent {
    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "household");
    }

    @Override
    protected void takeDown() {
        AgentHelper.deregisterAgent(this);
    }
}
