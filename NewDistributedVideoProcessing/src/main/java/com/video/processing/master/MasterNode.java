package com.video.processing.master;

import com.video.processing.common.Config;
import com.video.processing.common.BaseFrame;
import com.video.processing.common.ProcessedFrame;
import com.video.processing.common.VideoProcessor;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.opencv.core.CvType;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class MasterNode {
    private final ExecutorService executor;
    private final FrameDistributor distributor;

    BlockingQueue<BaseFrame> frameQueue = new LinkedBlockingQueue<>(50); // Max 50 frames in memory

    public MasterNode() {
        this.executor = Executors.newFixedThreadPool(Config.NUM_WORKERS);
        this.distributor = new FrameDistributor();
    }

    public void processVideo(String inputFile) throws Exception {
        // Read and stream frames directly to workers
        readVideoFile(inputFile);
        // Start worker threads
        List<Future<List<ProcessedFrame>>> futures = new ArrayList<>();
        for (int i = 0; i < Config.NUM_WORKERS; i++) {
            futures.add(executor.submit(() -> {
                System.out.println("Worker thread started.");
                return processFrames(); // processFrames must return a value here, e.g., List<ProcessedFrame>
            }));
        }

        // Ensure all workers finish processing
        executor.shutdown();
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Collect results
        List<ProcessedFrame> processedFrames = collectResults(futures);

        // Reconstruct video
        reconstructVideo(processedFrames);
    }

    private void readVideoFile(String inputFile) throws Exception {
        VideoProcessor processor = new VideoProcessor();
        processor.extractFrames(inputFile, frame -> {
            try {
                System.out.println("Reading frame: " + frame.timestamp);
                frameQueue.put(new BaseFrame(frame, frameQueue.size(), System.currentTimeMillis()));
                frame.close(); // Immediately release memory after adding to queue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private List<ProcessedFrame> collectResults(List<Future<List<ProcessedFrame>>> futures) throws Exception {
        List<ProcessedFrame> results = new ArrayList<>();
        for (Future<List<ProcessedFrame>> future : futures) {
            try {
                List<ProcessedFrame> futureResults = future.get();  // Blocks until the task completes
                if (futureResults != null && !futureResults.isEmpty()) {
                    results.addAll(futureResults);
                    System.out.println("Collected " + futureResults.size() + " processed frames.");
                } else {
                    System.out.println("Future returned null or empty results.");
                }
            } catch (Exception e) {
                System.err.println("Error getting result from future: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return results;
    }

    private void verifyWritePermissions(String directoryPath) throws IOException {
        // Create File object for the directory
        File directory = new File(directoryPath);

        // Check if directory exists
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory: " + directoryPath);
            }
        }

        // Check if directory is writable
        if (!directory.canWrite()) {
            throw new IOException("Directory is not writable: " + directoryPath);
        }

        // Verify we can actually write a test file
        File testFile = new File(directoryPath + "/test_write_permissions.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("test");
        } finally {
            // Clean up test file
            if (testFile.exists() && !testFile.delete()) {
                System.err.println("Warning: Could not delete test file");
            }
        }
    }

    private void reconstructVideo(List<ProcessedFrame> processedFrames) throws Exception {
        // Sort frames by frame number
        List<ProcessedFrame> sortedFrames = new ArrayList<>(processedFrames);
        Collections.sort(sortedFrames, Comparator.comparingInt(ProcessedFrame::getFrameNumber));

        // Create output directory
        File outputDir = new File(Config.VIDEO_OUTPUT_PATH);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Determine video properties
        ProcessedFrame firstFrame = sortedFrames.get(0);
        Frame inputFrame = firstFrame.getImage();

        // Output file
        String outputFilePath = Config.VIDEO_OUTPUT_PATH + "processed_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";

        // FFmpegFrameRecorder setup
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFilePath, inputFrame.imageWidth, inputFrame.imageHeight);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(30);
        recorder.setFormat("mp4");
        recorder.setVideoBitrate(1000000); // Set bitrate explicitly

        try {
            recorder.start();
            System.out.println("Recorder started successfully");

            Java2DFrameConverter converter = new Java2DFrameConverter();

            // Record frames
            for (ProcessedFrame frame : sortedFrames) {
                Frame image = frame.getImage();
                if (image == null) {
                    System.err.println("Skipping null frame at position " + frame.getFrameNumber());
                    continue;
                }
                OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat();
                Mat javacvMat = toMatConverter.convert(image);  // Convert Frame to JavaCV Mat

                // Convert JavaCV Mat to OpenCV Mat
                org.opencv.core.Mat opencvMat = javacvMatToOpenCVMat(javacvMat);

                BufferedImage bufferedImage = null;
                try {
                    bufferedImage = matToBufferedImage(opencvMat);
                } catch (Exception e) {
                    System.err.println("Frame conversion error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();  // Free memory
                    }
                }



                // Convert BufferedImage back to Frame
                Frame convertedFrame = converter.convert(bufferedImage);

                try {
                    recorder.record(convertedFrame);
                    System.out.println("Recorded frame " + frame.getFrameNumber());
                } catch (Exception e) {
                    System.err.println("Error recording frame " + frame.getFrameNumber() + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (convertedFrame != null) {
                        convertedFrame.close();
                    }
                    if (bufferedImage != null) {
                        bufferedImage.flush();
                    }
                }
            }

        } finally {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        }
    }


    // Convert JavaCV Mat to OpenCV Mat
    public static org.opencv.core.Mat javacvMatToOpenCVMat(Mat javacvMat) {
        // Convert JavaCV Mat to byte array
        MatOfByte mob = new MatOfByte();
        byte[] data = new byte[(int) javacvMat.total() * javacvMat.channels()];
        javacvMat.data().get(data);

        // Create OpenCV Mat from byte array
        org.opencv.core.Mat openCVMat = new org.opencv.core.Mat(javacvMat.rows(), javacvMat.cols(), CvType.CV_8UC3);
        openCVMat.put(0, 0, data);
        return openCVMat;
    }

    // Convert OpenCV Mat to BufferedImage
    public static BufferedImage matToBufferedImage(org.opencv.core.Mat mat) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".png", mat, mob);
        try {
            return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }




    private List<ProcessedFrame> processFrames() {
        List<ProcessedFrame> processedFrames = new ArrayList<>();
        try {
            while (true) {
                System.out.println("Processing frame.. " + frameQueue.size());
                BaseFrame frame = frameQueue.poll(2, TimeUnit.SECONDS); // Timeout prevents deadlock
                if (frame == null) break;

                // Process the frame
                String processingResult = processFrame(frame);
                long processingTimeMs = System.currentTimeMillis();

                processedFrames.add(new ProcessedFrame(frame, processingResult, processingTimeMs));
                System.out.println("after.. " + processedFrames);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return processedFrames;
    }

    private String processFrame(BaseFrame frame) {
        // Implement your frame processing logic here
        // For example: apply filters, detect objects, etc.
        System.out.println("Processing frame " + frame.getFrameNumber());
        return "Processed frame " + frame.getFrameNumber();
    }

    public static void main(String[] args) {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            MasterNode masterNode = new MasterNode();
            masterNode.processVideo("C:\\Users\\mthok\\Downloads\\Video\\1de84bafc93642b29c4e86f3dd24cba1.mp4"); // Replace with your input file
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
