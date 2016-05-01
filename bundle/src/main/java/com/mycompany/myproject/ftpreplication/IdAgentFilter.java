package com.mycompany.myproject.ftpreplication;

import com.day.cq.replication.Agent;
import com.day.cq.replication.AgentFilter;

public class IdAgentFilter implements AgentFilter {
    private String agentId;

    public IdAgentFilter(String id){
        this.agentId = id;
    }

    public boolean isIncluded(final Agent agent) {
        return agentId.equals(agent.getId());
    }
}

