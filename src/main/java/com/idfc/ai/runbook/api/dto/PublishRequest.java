package com.idfc.ai.runbook.api.dto;
import jakarta.validation.constraints.NotBlank;
public record PublishRequest(@NotBlank String mode,@NotBlank String deployedCommitSha,String deployedImageTag) {}
