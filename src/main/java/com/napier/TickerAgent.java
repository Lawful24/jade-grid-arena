package com.napier;

import jade.core.Agent;

public class TickerAgent extends Agent {
    @Override
    protected void setup() {
        AgentAction.registerAgent(this, "ticker");
    }

    @Override
    protected void takeDown() {
        AgentAction.deregisterAgent(this);
    }
}
