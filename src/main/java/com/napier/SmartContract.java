package com.napier;

import jade.core.Agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SmartContract {
    private static SmartContract instance;

    public static SmartContract getInstance() {
        if (instance == null) {
            instance = new SmartContract();
        }

        return instance;
    }

    private SmartContract() {

    }

    private void execute() {

    }

    private void createNewBlock(Transaction transaction) {
        String transactionString = transaction.toString();
        byte[] hashByteArray = null;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            hashByteArray = messageDigest.digest(transactionString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }

        if (hashByteArray != null) {
            BlockchainSingleton.getInstance().registerNewTransaction(bytesToHex(hashByteArray));
        } else {
            System.err.println("The new block was not added to the blockchain.");
        }
    }

    private void removeTimeslotsFromAdverts() {
        // TODO: how
    }

    private void finaliseExchange(HouseholdAgent requester, HouseholdAgent receiver, Transaction transaction) {
        // TODO: for each agent
    }

    // TODO: Cite https://www.baeldung.com/sha-256-hashing-java#:~:text=The%20SHA%20(Secure%20Hash%20Algorithm,text%20or%20a%20data%20file.
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);

        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);

            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        return hexString.toString();
    }
}
