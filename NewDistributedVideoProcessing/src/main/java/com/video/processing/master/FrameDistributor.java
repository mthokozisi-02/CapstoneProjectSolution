package com.video.processing.master;

import com.video.processing.common.BaseFrame;
import com.video.processing.common.Config;

import java.util.*;

public class FrameDistributor {
    public Map<Integer, List<BaseFrame>> distribute(List<BaseFrame> frames) {
        Map<Integer, List<BaseFrame>> assignments = new HashMap<>();

        // Calculate frames per worker
        int framesPerWorker = frames.size() / Config.NUM_WORKERS;

        // Distribute frames evenly
        for (int workerId = 0; workerId < Config.NUM_WORKERS; workerId++) {
            int start = workerId * framesPerWorker;
            int end = (workerId == Config.NUM_WORKERS - 1)
                    ? frames.size()
                    : (workerId + 1) * framesPerWorker;

            assignments.put(workerId, frames.subList(start, end));
        }

        return assignments;
    }
}