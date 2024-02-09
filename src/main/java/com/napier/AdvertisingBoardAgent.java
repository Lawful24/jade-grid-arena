package com.napier;

import jade.core.Agent;

public class AdvertisingBoardAgent extends Agent {
    @Override
    protected void setup() {
        AgentAction.registerAgent(this, "advertising-board");
    }

    @Override
    protected void takeDown() {
        AgentAction.deregisterAgent(this);
    }
}
