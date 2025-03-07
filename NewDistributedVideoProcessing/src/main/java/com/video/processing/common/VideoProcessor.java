package com.video.processing.common;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {
    private FFmpegFrameGrabber grabber;
    private Java2DFrameConverter converter;

    public VideoProcessor() {
        // Load native libraries first
        Loader.load(avcodec.class);
        Loader.load(avutil.class);

        this.converter = new Java2DFrameConverter();
    }

    public void extractFrames(String inputFile, FrameProcessor processor) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile)) {
            grabber.start();

            // Log video info
            System.out.println("Video format: " + grabber.getFormat());
            System.out.println("Video frame rate: " + grabber.getFrameRate());


            Frame frame;
            while (true) {
                frame = grabber.grabImage();

                // Check if we have reached the end of the video stream
                if (frame.timestamp == 1666666) {
                    System.out.println("End of video or invalid frame detected. Exiting loop.");
                    break;  // Exit the loop when the end of the video is reached or an invalid frame is detected
                }

                // Debugging: Show information about the current frame
                System.out.println("Received frame with image: " + frame.image);
                System.out.println("Processing frame with timestamp: " + frame.timestamp);

                if (frame.image != null) {
                    processor.process(frame.clone());
                    frame.close();  // Release memory immediately after processing
                }

                // Optionally, add a sleep or delay to prevent overloading the CPU
                // Thread.sleep(10);
            }


            grabber.stop();
        }
    }




    @FunctionalInterface
    public interface FrameProcessor {
        void process(Frame frame);
    }

}