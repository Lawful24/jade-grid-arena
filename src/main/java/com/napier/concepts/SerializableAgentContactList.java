package com.napier.concepts;

import java.io.Serializable;
import java.util.ArrayList;

public record SerializableAgentContactList(ArrayList<AgentContact> contacts) implements Serializable {
    // no-op
}
