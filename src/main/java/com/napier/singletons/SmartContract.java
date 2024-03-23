package com.napier.singletons;

import com.napier.AgentHelper;
import com.napier.agents.HouseholdAgent;
import com.napier.concepts.TradeOffer;
import com.napier.concepts.Transaction;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class SmartContract {
    private static SmartContract instance;
    private final ArrayList<TradeOffer> unprocessedTradeOffersQueue;

    public static SmartContract getInstance() {
        if (instance == null) {
            instance = new SmartContract();
        }

        return instance;
    }

    private SmartContract() {
        this.unprocessedTradeOffersQueue = new ArrayList<>();
    }

    public void triggerSmartContract(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        finaliseExchange(receiverAgentObject, acceptedTradeOffer);
    }

    public void finishSmartContract(TradeOffer finalisedTradeOffer, boolean doesReceiverGainSocialCapita, boolean doesRequesterLoseSocialCapita) {
        createNewBlock(new Transaction(
                finalisedTradeOffer.requesterAgent(),
                finalisedTradeOffer.receiverAgent(),
                finalisedTradeOffer.timeSlotRequested(),
                finalisedTradeOffer.timeSlotOffered(),
                doesReceiverGainSocialCapita,
                doesRequesterLoseSocialCapita
        ));
    }

    private void finaliseExchange(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        final boolean doesRequesterLoseSocialCapita = receiverAgentObject.completeReceivedExchange(acceptedTradeOffer);

        Behaviour finaliseTradeSCBehaviour = new Behaviour() {
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
                            // Make sure the incoming object is readable
                            Serializable incomingObject = null;

                            try {
                                incomingObject = incomingSyncMessage.getContentObject();
                            } catch (UnreadableException e) {
                                AgentHelper.printAgentError(myAgent.getLocalName(), "Incoming acknowledged trade offer is unreadable: " + e.getMessage());
                            }

                            if (incomingObject != null) {
                                // Make sure the incoming object is of the expected type
                                if (incomingObject instanceof TradeOffer acknowledgedTradeOffer) {
                                    boolean doesReceiverGainSocialCapita = Boolean.parseBoolean(incomingSyncMessage.getConversationId());

                                    if (doesReceiverGainSocialCapita) {
                                        receiverAgentObject.incrementTotalSocialCapita();
                                    }

                                    SmartContract.getInstance().finishSmartContract(acknowledgedTradeOffer, doesReceiverGainSocialCapita, doesRequesterLoseSocialCapita);
                                } else {
                                    AgentHelper.printAgentError(myAgent.getLocalName(), "Acknowledged trade offer cannot be processed: the received object has an incorrect type.");
                                }
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
        };

        receiverAgentObject.addBehaviour(finaliseTradeSCBehaviour);
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
