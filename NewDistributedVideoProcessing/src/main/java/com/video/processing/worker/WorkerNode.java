package com.video.processing.worker;

import com.video.processing.common.BaseFrame;
import com.video.processing.common.ProcessedFrame;
import com.video.processing.common.Config;
import org.bytedeco.javacv.Frame;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.Color;

public class WorkerNode {
    private final ExecutorService threadPool;
    private static final Logger logger = Logger.getLogger(WorkerNode.class.getName());

    public WorkerNode(int numThreads) {
        this.threadPool = Executors.newFixedThreadPool(numThreads);
        logger.log(Level.INFO, "Worker node initialized with {0} threads", numThreads);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public List<ProcessedFrame> processFrames(List<BaseFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            logger.log(Level.WARNING, "Empty or null frame list received");
            return new ArrayList<>();
        }

        List<Future<ProcessedFrame>> futures = new ArrayList<>();

        for (BaseFrame frame : frames) {
            futures.add(threadPool.submit(() -> processFrame(frame)));
        }

        return futures.stream()
                .map(this::getProcessedFrame)
                .collect(Collectors.toList());
    }

    private ProcessedFrame processFrame(BaseFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("BaseFrame cannot be null");
        }

        try {
            // Get the frame image
            Frame image = frame.getImage();
            if (image == null) {
                throw new IllegalArgumentException("Frame image is null");
            }

            // Process the frame colors
            Color borderColor = processColor(frame);
            Color fillColor = processColor(frame); // You might want different logic for fill color

            // Create and return the ProcessedFrame
            return new ProcessedFrame(
                    frame,                    // BaseFrame original
                    "processed",             // String processingResult
                    System.currentTimeMillis() // long processingTimeMs
            );

        } catch (Exception e) {
            throw new RuntimeException("Error processing frame: " + e.getMessage(), e);
        }
    }

    private ProcessedFrame getProcessedFrame(Future<ProcessedFrame> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error retrieving processed frame", e);
            throw new RuntimeException("Error processing frame", e);
        }
    }

    public void start() {
        logger.log(Level.INFO, "Worker node started. Waiting for frames to process...");

        // Keep the main thread alive
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Wait for work or check for shutdown
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.log(Level.INFO, "Worker node interrupted, shutting down...");
                shutdown();
                break;
            }
        }
    }

    private void shutdown() {
        logger.log(Level.INFO, "Initiating worker node shutdown...");
        threadPool.shutdown();

        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Forcing thread pool termination");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.log(Level.INFO, "Worker node shutdown completed");
    }

    public static void main(String[] args) {
        WorkerNode worker = new WorkerNode(4);
        worker.start(); // Call start() instead of just creating the node
    }

    public class Java2DFrameConverter {
        private final org.bytedeco.javacv.Java2DFrameConverter converter;

        public Java2DFrameConverter() {
            this.converter = new org.bytedeco.javacv.Java2DFrameConverter();
        }

        public BufferedImage getBufferedImage(Frame frame) {
            if (frame == null) {
                throw new IllegalArgumentException("Frame cannot be null");
            }

            return converter.convert(frame);
        }
    }

    private Color processColor(BaseFrame frame) {
        Frame image = frame.getImage();
        if (image == null) {
            return Color.BLACK; // Default color
        }

        // Convert to Java 2D image
        Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage bufferedImage = converter.getBufferedImage(image);

        // Process the image data
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // Example: Get average color of the border
        int red = 0, green = 0, blue = 0;
        int count = 0;

        // Process border pixels
        for (int x = 0; x < width; x++) {
            // Top border
            int rgb = bufferedImage.getRGB(x, 0);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            count++;

            // Bottom border
            rgb = bufferedImage.getRGB(x, height - 1);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            count++;
        }

        // Left and right borders
        for (int y = 1; y < height - 1; y++) {
            int rgb = bufferedImage.getRGB(0, y);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            count++;

            rgb = bufferedImage.getRGB(width - 1, y);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            count++;
        }

        // Calculate average color
        red /= count;
        green /= count;
        blue /= count;

        return new Color(red, green, blue);
    }
}