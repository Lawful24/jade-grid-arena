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
            container.createNewAgent("rma",
                    "jade.tools.rma.rma",
                    null
            ).start();

            container.createNewAgent(
                    "Ticker",
                    TickerAgent.class.getCanonicalName(),
                    null
            ).start();

            container.createNewAgent(
                    "Board",
                    AdvertisingBoardAgent.class.getCanonicalName(),
                    null
            ).start();

            for (int i = 1; i <= config.getPopulationCount(); i++) {
                AgentStrategyType type;

                if (i <= config.getSelfishPopulationCount()) {
                    type = AgentStrategyType.SELFISH;
                } else {
                    type = AgentStrategyType.SOCIAL;
                }

                container.createNewAgent(
                        "Household-" + i,
                        HouseholdAgent.class.getCanonicalName(),
                        new Object[] {
                            type
                        }
                ).start();
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}