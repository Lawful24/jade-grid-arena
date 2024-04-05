package com.napier.arena;

import com.napier.arena.agents.AdvertisingBoardAgent;
import com.napier.arena.agents.HouseholdAgent;
import com.napier.arena.agents.TickerAgent;
import com.napier.arena.singletons.SimulationConfigurationSingleton;
import com.napier.arena.types.ExchangeType;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

/**
 * The main class and entrypoint of the application.
 *
 * @author László Tárkányi
 */
public class Main {
    private static boolean debugMode;
    private static ExchangeType defaultExchangeType = ExchangeType.MessagePassing;

    public static void main(String[] args) {
        debugMode = false;

        // Check the command line arguments and determine the execution mode based on them
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

    /**
     * Checks if the application should run in Debug Mode. Agents in Debug Mode print additional logs to the console.
     *
     * @return (boolean) Whether the application should be running in Debug Mode or not.
     */
    public static boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Gets the default Exchange Type.
     * The default type can be changed by assigning a different enum value to the defaultExchangeType attribute.
     *
     * @return (ExchangeType) The default Exchange Type that should be used in the application.
     */
    public static ExchangeType getDefaultExchangeType() {
        return defaultExchangeType;
    }

    private static void initEnvironment() {
        // Create the first instance of the configuration singleton before the agent threads can access it
        SimulationConfigurationSingleton config = SimulationConfigurationSingleton.getInstance();

        /*
        The source for creating and initialising the agents participating in the simulation:
        Dr Simon Powers: SET10111 Multi-Agent Systems - Practical textbook; Edinburgh Napier University
        */

        // Set up and create the main agent container
        Profile profile = new ProfileImpl();
        Runtime runtime = Runtime.instance();

        // Overwrite the default maximum value of agents in the main container
        // It is originally 100
        // This needs to be modified for running simulations with 200+ agents
        profile.setParameter("jade_domain_df_maxresult", "200");
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