package com.example.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Image attachment carried with a user message. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAttachment(
        String name,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("data_url") String dataUrl,
        Long size
) {}
