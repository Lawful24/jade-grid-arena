package com.napier;

import jade.core.Agent;

public class TickerAgent extends Agent {
    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "ticker");
    }

    @Override
    protected void takeDown() {
        AgentHelper.deregisterAgent(this);
    }
}
