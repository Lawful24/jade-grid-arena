package com.napier.concepts;

import jade.core.AID;

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