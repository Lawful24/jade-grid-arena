package com.napier;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;

public class Main {
    public static void main(String[] args) {
        initEnvironment();
    }

    private static void initEnvironment() {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();
        // ^ Debug mode can be toggled in the RunConfigurationSingleton class

        // TODO: Comments and cite the JADE workbook
        Profile profile = new ProfileImpl();
        Runtime runtime = Runtime.instance();
        ContainerController container = runtime.createMainContainer(profile);

        try {
            // Create and start the Remote Monitoring Agent
            container.createNewAgent("rma",
                    "jade.tools.rma.rma",
                    null
            ).start();

            // Create and start the Ticker agent
            container.createNewAgent(
                    "Ticker",
                    TickerAgent.class.getCanonicalName(),
                    null
            ).start();

            // Create and start the Advertising Board agent
            container.createNewAgent(
                    "Board",
                    AdvertisingBoardAgent.class.getCanonicalName(),
                    null
            ).start();

            // Create as many Household agents as defined in the config.properties file (population.size)
            for (int i = 1; i <= config.getPopulationCount(); i++) {
                // Set the nickname and the type based on the index in the population
                container.createNewAgent(
                        "Household-" + i,
                        HouseholdAgent.class.getCanonicalName(),
                        new Object[] {
                            AgentHelper.determineAgentType(i)
                        }
                ).start();
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}