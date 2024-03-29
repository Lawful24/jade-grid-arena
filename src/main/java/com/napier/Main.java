package com.napier;

import com.napier.agents.AdvertisingBoardAgent;
import com.napier.agents.HouseholdAgent;
import com.napier.agents.TickerAgent;
import com.napier.singletons.SimulationConfigurationSingleton;
import com.napier.types.ExchangeType;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class Main {
    private static boolean debugMode;
    private static ExchangeType defaultExchangeType;

    public static void main(String[] args) {
        debugMode = false;
        defaultExchangeType = ExchangeType.MessagePassing;

        if (args.length == 0 || args.length == 1) {
            if (args.length == 1) {
                if (args[0].equals("--debug")) {
                    debugMode = true;
                } else {
                    defaultExchangeType = null;

                    System.err.println("The first argument can only be \"--debug\".");
                }
            }

            if (defaultExchangeType != null) {
                initEnvironment();
            }
        } else {
            System.err.println("Incorrect list of arguments. The only permitted optional command line argument is \"--debug\"");
        }
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static ExchangeType getDefaultExchangeType() {
        return defaultExchangeType;
    }

    private static void initEnvironment() {
        // Create the first instance of the configuration singleton before the agent threads can access it
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        // TODO: Cite the JADE workbook
        // Set up and create the main agent container
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
                        null
                ).start();
            }
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
}