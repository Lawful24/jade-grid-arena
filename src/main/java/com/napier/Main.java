package com.napier;

import com.napier.agents.AdvertisingBoardAgent;
import com.napier.agents.HouseholdAgent;
import com.napier.agents.TickerAgent;
import com.napier.singletons.RunConfigurationSingleton;
import com.napier.singletons.SimulationDataOutputSingleton;
import com.napier.types.ExchangeType;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;

public class Main {
    private static boolean debugMode;
    private static ExchangeType exchangeType;

    public static void main(String[] args) {
        debugMode = false;

        if (args.length == 1 || args.length == 2) {
            exchangeType = parseType(args[0]);

            if (args.length == 2) {
                if (args[1].equals("--debug")) {
                    debugMode = true;
                } else {
                    exchangeType = null;

                    System.err.println("The second argument can only be \"--debug\".");
                }
            }

            if (exchangeType != null) {
                initEnvironment();
            }
        } else {
            System.err.println("Incorrect list of arguments. Please try providing an exchange type.");
            System.err.println("The available flags are: \"--MessagePassing\" and \"--SmartContract\".");
        }
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static ExchangeType getExchangeType() {
        return exchangeType;
    }

    private static ExchangeType parseType(String input) {
        if (input.equals("--MessagePassing")) {
            return ExchangeType.MessagePassing;
        } else if (input.equals("--SmartContract")) {
            return ExchangeType.SmartContract;
        }

        System.err.println("Invalid exchange type. The available flags are: \"--MessagePassing\" and \"--SmartContract\".");

        return null;
    }

    private static void initEnvironment() {
        RunConfigurationSingleton config = RunConfigurationSingleton.getInstance();

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
                        null
                ).start();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}