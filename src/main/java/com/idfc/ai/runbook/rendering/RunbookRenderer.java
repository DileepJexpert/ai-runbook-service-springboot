package com.idfc.ai.runbook.rendering; import com.fasterxml.jackson.databind.JsonNode; public interface RunbookRenderer { String render(JsonNode data); }
