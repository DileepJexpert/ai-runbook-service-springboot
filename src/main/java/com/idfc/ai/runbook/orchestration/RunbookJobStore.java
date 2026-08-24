package com.idfc.ai.runbook.orchestration; import java.util.*;
public interface RunbookJobStore { RunbookJob save(RunbookJob job); Optional<RunbookJob> get(UUID id); }
