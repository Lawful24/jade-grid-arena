package com.napier.singletons;

import com.napier.AgentHelper;
import com.napier.agents.HouseholdAgent;
import com.napier.concepts.TradeOffer;
import com.napier.concepts.Transaction;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;

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

    public void triggerSmartContract(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        // TODO: execute the contract here
        Transaction finalisedTransaction = finaliseExchange(receiverAgentObject, acceptedTradeOffer);
        createNewBlock(finalisedTransaction);
    }

    private Transaction finaliseExchange(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        final boolean doesRequesterLoseSocialCapita = receiverAgentObject.completeReceivedExchange(acceptedTradeOffer);
        final boolean[] doesReceiverGainSocialCapita = new boolean[1];

        receiverAgentObject.addBehaviour(new Behaviour() {
            private int step = 0;

            @Override
            public void action() {
                switch (step) {
                    case 0:
                        AgentHelper.sendMessage(
                                myAgent,
                                acceptedTradeOffer.requesterAgent(),
                                Boolean.toString(doesRequesterLoseSocialCapita),
                                acceptedTradeOffer,
                                ACLMessage.ACCEPT_PROPOSAL
                        );

                        step++;

                    case 1:
                        ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, acceptedTradeOffer.requesterAgent(), ACLMessage.INFORM_IF);

                        if (incomingSyncMessage != null) {
                            if (Boolean.parseBoolean(incomingSyncMessage.getConversationId())) {
                                receiverAgentObject.incrementTotalSocialCapita();
                            }

                            step++;
                        } else {
                            block();
                        }
                }
            }

            @Override
            public boolean done() {
                return step == 2;
            }

            @Override
            public int onEnd() {
                if (RunConfigurationSingleton.getInstance().isDebugMode()) {
                    AgentHelper.printAgentLog(myAgent.getLocalName(), "finished finalising the exchange");
                }

                return 0;
            }
        });

        if (doesReceiverGainSocialCapita[0]) { // todo: so apparently this never gets overwritten
            receiverAgentObject.incrementTotalSocialCapita();
        }

        return new Transaction(
                acceptedTradeOffer.requesterAgent(),
                acceptedTradeOffer.receiverAgent(),
                acceptedTradeOffer.timeSlotRequested(),
                acceptedTradeOffer.timeSlotOffered(),
                doesReceiverGainSocialCapita[0],
                doesRequesterLoseSocialCapita
        );
    }

    private void createNewBlock(Transaction transaction) {
        String transactionString = transaction.toString();
        byte[] hashByteArray = null;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            hashByteArray = messageDigest.digest(transactionString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }

        if (hashByteArray != null) {
            BlockchainSingleton.getInstance().registerNewTransaction(bytesToHex(hashByteArray));
        } else {
            System.err.println("The new block was not added to the blockchain as it was not encrypted.");
        }
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
