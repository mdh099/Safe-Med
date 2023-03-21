package edu.ucf.safemed.tflite;

import android.content.res.AssetManager;

import java.io.IOException;

import edu.ucf.safemed.Constants;

public class DetectorFactory {
    public static YoloV5Classifier getDetector(
            final AssetManager assetManager,
            final String modelFilename)
            throws IOException {
        String labelFilename = null;
        boolean isQuantized = false;
        int inputSize = Constants.input_size;
        int[] output_width = new int[]{80, 40, 20};
        int[][]  masks = new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
        int[] anchors = new int[]{
                10,13, 16,30, 33,23, 30,61, 62,45, 59,119, 116,90, 156,198, 373,326
        };

        return YoloV5Classifier.create(assetManager, modelFilename, Constants.CUSTOM_CLASSES_PATH, isQuantized,
                inputSize);
    }

}
