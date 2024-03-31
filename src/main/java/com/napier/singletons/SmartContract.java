package com.napier.singletons;

import com.napier.AgentHelper;
import com.napier.agents.HouseholdAgent;
import com.napier.concepts.TradeOffer;
import com.napier.concepts.Transaction;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Represents a Smart Contract in a Blockchain that is executed when certain conditions are met within the application.
 *
 * @author László Tárkányi
 */
public class SmartContract {
    private static SmartContract instance;

    public static SmartContract getInstance() {
        if (instance == null) {
            instance = new SmartContract();
        }

        return instance;
    }

    private SmartContract() {
        // no-op
    }

    /**
     * Should be called when the receiver Household agent of a trade offer accepts said trade offer.
     *
     * @param receiverAgentObject The Agent object of the Household agent accepting the trade offer.
     * @param acceptedTradeOffer The trade offer that got accepted.
     */
    public void triggerSmartContract(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        this.finaliseExchange(receiverAgentObject, acceptedTradeOffer);
    }

    /**
     * Used to create a transaction after the exchange based on the accepted trade offer has been finalised.
     *
     * @param finalisedTradeOffer The trade offer that got accepted and was processed by the smart contract.
     * @param doesReceiverGainSocialCapita Whether the Household agent who received the trade gains social capita based on the accepted trade or not.
     * @param doesRequesterLoseSocialCapita Whether the Household agent who requested the trade loses social capita based on the accepted trade or not.
     */
    public void finishSmartContract(TradeOffer finalisedTradeOffer, boolean doesReceiverGainSocialCapita, boolean doesRequesterLoseSocialCapita) {
        this.createNewBlock(new Transaction(
                finalisedTradeOffer.requesterAgent(),
                finalisedTradeOffer.receiverAgent(),
                finalisedTradeOffer.timeSlotRequested(),
                finalisedTradeOffer.timeSlotOffered(),
                doesReceiverGainSocialCapita,
                doesRequesterLoseSocialCapita
        ));
    }

    /**
     * Creates a new behaviour that is executed by the receiving Household agent.
     * This behaviour transfers the ownership of the timeslots that are being traded and adjusts the social capita for both parties.
     *
     * @param receiverAgentObject The Agent object of the Household agent accepting the trade offer.
     * @param acceptedTradeOffer The trade offer that got accepted.
     */
    private void finaliseExchange(HouseholdAgent receiverAgentObject, TradeOffer acceptedTradeOffer) {
        // Finalise the exchange
        final boolean doesRequesterLoseSocialCapita = receiverAgentObject.completeReceivedExchange(acceptedTradeOffer);

        // Create a new behaviour that is to be added to the receiving agent's behaviour queue
        Behaviour finaliseTradeSCBehaviour = new Behaviour() {
            private int step = 1;

            @Override
            public void action() {
                /*
                The source of the step logic:
                Dr Simon Powers: SET10111 Multi-Agent Systems - Practical textbook; Edinburgh Napier University
                */

                switch (step) {
                    // Step 1: Reply to the requester
                    case 1:
                        // Inform the requester that the offer has been accepted and whether it should lose social capita as a result of the trade
                        AgentHelper.sendMessage(
                                myAgent,
                                acceptedTradeOffer.requesterAgent(),
                                Boolean.toString(doesRequesterLoseSocialCapita),
                                acceptedTradeOffer,
                                ACLMessage.ACCEPT_PROPOSAL
                        );

                        // Progress the state of the behaviour
                        step++;

                        break;
                    // Step 2: Receive an answer from the requester
                    case 2:
                        // Listen for the reply from the requester agent
                        ACLMessage incomingSyncMessage = AgentHelper.receiveMessage(myAgent, acceptedTradeOffer.requesterAgent(), ACLMessage.INFORM_IF);

                        if (incomingSyncMessage != null) {
                            // Make sure the incoming object is readable
                            Serializable receivedObject = AgentHelper.readReceivedContentObject(incomingSyncMessage, myAgent.getLocalName(), TradeOffer.class);

                            // Make sure the incoming object is of the expected type
                            if (receivedObject instanceof TradeOffer acknowledgedTradeOffer) {
                                // Parse the reply
                                boolean doesReceiverGainSocialCapita = Boolean.parseBoolean(incomingSyncMessage.getConversationId());

                                // Adjust the receiver's total social capita accordingly
                                if (doesReceiverGainSocialCapita) {
                                    receiverAgentObject.incrementTotalSocialCapita();
                                }

                                // Let the smart contract process the now finalised and processed trade offer
                                SmartContract.getInstance().finishSmartContract(acknowledgedTradeOffer, doesReceiverGainSocialCapita, doesRequesterLoseSocialCapita);
                            } else {
                                AgentHelper.printAgentError(myAgent.getLocalName(), "Acknowledged trade offer cannot be processed: the received object has an incorrect type or is null.");
                            }

                            // Progress the state of the behaviour
                            step++;
                        } else {
                            block();
                        }

                        break;
                    default:
                        break;
                }
            }

            @Override
            public boolean done() {
                return step == 3;
            }

            @Override
            public int onEnd() {
                if (SimulationConfigurationSingleton.getInstance().isDebugMode()) {
                    AgentHelper.printAgentLog(myAgent.getLocalName(), "finished finalising the exchange");
                }

                return 0;
            }
        };

        // Add the created behaviour to the receiver agent's behaviour queue
        receiverAgentObject.addBehaviour(finaliseTradeSCBehaviour);
    }

    /**
     * Notifies the blockchain about a new successful transaction and encrypts it to verify its authenticity.
     *
     * @param transaction The object containing details about the transfer of ownership.
     */
    private void createNewBlock(Transaction transaction) {
        String transactionString = transaction.toString();
        byte[] hashByteArray = null;

        // Encrypt the transaction of the trade offer using SHA-256
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            hashByteArray = messageDigest.digest(transactionString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (hashByteArray != null) {
            // Add the authorised transaction to the blockchain ledger
            BlockchainSingleton.getInstance().registerNewTransaction(bytesToHex(hashByteArray));
        } else {
            System.err.println("The new block was not added to the blockchain as it was not encrypted.");
        }
    }

    /**
     * @see <a href="https://www.baeldung.com/sha-256-hashing-java#:~:text=The%20SHA%20">Baeldung: SHA-256 and SHA3-256 Hashing in Java</a>
     *
     * @param hash The byte array representation of an encrypted Transaction object.
     * @return The hexadecimal representation of the provided byte array.
     */
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