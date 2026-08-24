package com.idfc.ai.runbook.diff; import java.util.*; public record OperationalDiff(boolean hasOperationalChanges,List<String> changedSections,Map<String,String> fingerprints) {}
