package com.napier;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {
    public static void main(String[] args) {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
        // ^ Debug mode can be toggled in the RunConfigurationSingleton class

        initEnvironment();
    }

    private static void initEnvironment() {
        // TODO: Comments and cite the JADE workbook
        Profile profile = new ProfileImpl();
        Runtime runtime = Runtime.instance();
        ContainerController container = runtime.createMainContainer(profile);

        try {
            AgentController rma = container.createNewAgent("rma", "jade.tools.rma.rma", null);
            rma.start();

            AgentController ticker = container.createNewAgent("Ticker", TickerAgent.class.getCanonicalName(), null);
            ticker.start();

            AgentController advertisingBoard = container.createNewAgent("Board", AdvertisingBoardAgent.class.getCanonicalName(), null);
            advertisingBoard.start();

            AgentController household = container.createNewAgent("Household", HouseholdAgent.class.getCanonicalName(), null);
            household.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}