package com.video.processing.common;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.util.UUID;

/**
 * Base class representing a video frame with metadata.
 */
public class BaseFrame implements Comparable<BaseFrame> {
    protected final UUID id;
    protected final Frame ffmpegFrame;
    protected final int frameNumber;
    protected final long timestamp;
    protected boolean processed;

    public BaseFrame(Frame ffmpegFrame, int frameNumber, long timestamp) {
        this.id = UUID.randomUUID();
        this.ffmpegFrame = ffmpegFrame;
        this.frameNumber = frameNumber;
        this.timestamp = timestamp;
        this.processed = false;
    }

    // Getters
    public UUID getId() { return id; }
    public Frame getFfmpegFrame() { return ffmpegFrame; }
    public int getFrameNumber() { return frameNumber; }
    public long getTimestamp() { return timestamp; }
    public boolean isProcessed() { return processed; }

    // New method to get the image
    public Frame getImage() {
        return ffmpegFrame;
    }

    // Setters
    public void setProcessed(boolean processed) { this.processed = processed; }

    @Override
    public int compareTo(BaseFrame other) {
        return Integer.compare(this.frameNumber, other.frameNumber);
    }

    @Override
    public String toString() {
        return String.format("Frame[id=%s, number=%d, timestamp=%d]",
                id.toString().substring(0, 8), frameNumber, timestamp);
    }
}