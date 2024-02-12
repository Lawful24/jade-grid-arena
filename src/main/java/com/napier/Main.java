package com.napier;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        // Retrieve user parameters from the config file.
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

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