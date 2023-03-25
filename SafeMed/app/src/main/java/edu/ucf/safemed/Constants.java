package edu.ucf.safemed;

public class Constants {
    public static String LINE_DETECT_MODEL = "line_count-416-version.tflite";
    public static String PLUNGER_DETECT_MODEL = "plunger_detect-416.tflite";
    public static String BARREL_DETECT_MODEL = "barrel_detect-416-version.tflite";
    public static String CUSTOM_CLASSES_PATH = "file:///android_asset/customclasses.txt";
    public static int barrel_input_size = 416;
    public static int plunger_input_size = 416;
    public static int line_input_size = 608;
}
