package com.napier;

import jade.core.AID;

import java.io.Serializable;
import java.util.ArrayList;

public record Advert(AID owner, TimeSlot[] timeSlotsForTrade) {
    // no-op
}
