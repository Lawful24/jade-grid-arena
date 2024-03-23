package com.napier.singletons;

import java.util.LinkedList;

public class BlockchainSingleton {
    private static BlockchainSingleton instance;

    /* Blockchain properties */
    private final LinkedList<String> hashedTransactions;

    public static BlockchainSingleton getInstance() {
        if (instance == null) {
            instance = new BlockchainSingleton();
        }

        return instance;
    }

    private BlockchainSingleton() {
        this.hashedTransactions = new LinkedList<>();
    }

    public void resetBlockchain() {
        this.hashedTransactions.clear();
    }

    public void registerNewTransaction(String hashString) {
        this.hashedTransactions.add(hashString);
    }
}
