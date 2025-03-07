package com.video.processing.common;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Represents a processed video frame with additional metadata about the processing.
 */
public class ProcessedFrame extends BaseFrame {
    private final String processingResult;
    private final long processingTimeMs;

    public ProcessedFrame(BaseFrame original, String processingResult, long processingTimeMs) {
        super(original.getImage(), original.getFrameNumber(), original.getTimestamp());
        this.processingResult = processingResult;
        this.processingTimeMs = processingTimeMs;
        setProcessed(true);
    }

    // Getters
    public String getProcessingResult() { return processingResult; }
    public long getProcessingTimeMs() { return processingTimeMs; }

    @Override
    public String toString() {
        return String.format("ProcessedFrame[id=%s, number=%d, result='%s', time=%dms]",
                getId().toString().substring(0, 8),
                getFrameNumber(),
                processingResult.substring(0, Math.min(processingResult.length(), 20)),
                processingTimeMs);
    }
}