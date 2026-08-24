package com.idfc.ai.runbook.confluence; import com.idfc.ai.runbook.config.RunbookProperties; public interface RunbookTargetRegistry { RunbookProperties.Target target(String service); }
