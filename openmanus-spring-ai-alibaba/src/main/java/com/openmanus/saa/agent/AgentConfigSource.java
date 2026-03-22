package com.openmanus.saa.agent;

import java.util.List;

public interface AgentConfigSource {

    List<AgentDefinition> loadAll();
}
