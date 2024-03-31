package com.napier.concepts;

import jade.core.AID;

/**
 * A wrapper that contains details about the transfer of ownership over timeslots.
 *
 * @author László Tárkányi
 *
 * @param requester
 * @param receiver
 * @param requested
 * @param received
 * @param doesReceiverGainSocialCapita
 * @param doesRequesterLoseSocialCapita
 */
public record Transaction (
        AID requester,
        AID receiver,
        TimeSlot requested,
        TimeSlot received,
        boolean doesReceiverGainSocialCapita,
        boolean doesRequesterLoseSocialCapita
) {
    // no-op
}