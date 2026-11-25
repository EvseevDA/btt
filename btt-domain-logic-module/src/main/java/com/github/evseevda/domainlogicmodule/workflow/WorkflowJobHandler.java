package com.github.evseevda.domainlogicmodule.workflow;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

public interface WorkflowJobHandler {
    Set<String> jobTypes();

    Map<String, ?> handle(String type, JsonNode variables);
}
