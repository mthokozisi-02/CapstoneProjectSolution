package com.video.processing.common;

public class Config {
    public static final String MASTER_HOST = "localhost";
    public static final int MASTER_PORT = 8000;
    public static final int NUM_WORKERS = 4; // Number of worker nodes
    public static final int FRAME_BATCH_SIZE = 30; // Frames per batch
    public static final String VIDEO_INPUT_PATH = "./input/";
    public static final String VIDEO_OUTPUT_PATH = "C:\\Users\\mthok\\Downloads\\Video\\";

    private Config() {} // Prevent instantiation
}