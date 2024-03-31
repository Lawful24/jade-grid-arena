package com.napier.singletons;

import java.util.LinkedList;

/**
 * Singleton class representing a blockchain ledger.
 *
 * @author László Tárkányi
 */
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

    /**
     * Clears all previously stored transactions.
     */
    public void resetBlockchain() {
        this.hashedTransactions.clear();
    }

    /**
     * Stores an encrypted transaction on the blockchain ledger.
     *
     * @param hashString The hashed Transaction object.
     */
    public void registerNewTransaction(String hashString) {
        this.hashedTransactions.add(hashString);
    }
}