package com.napier;

import jade.core.Agent;

public class AdvertisingBoardAgent extends Agent {
    @Override
    protected void setup() {
        AgentHelper.registerAgent(this, "advertising-board");
    }

    @Override
    protected void takeDown() {
        AgentHelper.deregisterAgent(this);
    }
}
