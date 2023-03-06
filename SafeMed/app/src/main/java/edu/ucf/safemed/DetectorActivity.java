package edu.ucf.safemed;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import edu.ucf.safemed.customview.OverlayView;
import edu.ucf.safemed.customview.OverlayView.DrawCallback;
import edu.ucf.safemed.env.BorderedText;
import edu.ucf.safemed.env.ImageUtils;
import edu.ucf.safemed.tflite.Classifier;
import edu.ucf.safemed.tflite.DetectorFactory;
import edu.ucf.safemed.tflite.YoloV5Classifier;
import edu.ucf.safemed.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = Logger.getLogger(DetectorActivity.class.getName());

    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.3f;
    private static final boolean MAINTAIN_ASPECT = true;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 640);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private YoloV5Classifier detector;
    private YoloV5Classifier detectorLines;
    private YoloV5Classifier detectorPlunger;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
    private AlertDialog loadingDialog;
    private AlertDialog syringeDialog;
    private AlertDialog infoDialog;
    private AlertDialog resultsDialog;

    private FloatingActionButton addSyringeButton;
    private FloatingActionButton infoButton;

    private TextView value;

    private String name = null, volume = null, units = null, numberOfLines = null;

    public void openDialog(){
        loadingDialog.show();
    }

    public void dismissDialog(){
        loadingDialog.dismiss();
    }

    public void resultsDialog(double valueToDisplay){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("REACHED: " + value);
                if (value != null) {
                    value.setText("" + valueToDisplay);
                    System.out.println("UPDATING: " + valueToDisplay);
                }
                resultsDialog.show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createSyringeDialog();
                createLoadingDialog();
                createInfoDialog();
                createResultsDialog();

                addSyringeButton = findViewById(R.id.add_syringe_button);
                addSyringeButton.setOnClickListener((view) -> {syringeDialog.show();});

                infoButton = findViewById(R.id.info_button);
                infoButton.setOnClickListener((view) -> {infoDialog.show();});
            }
        });

    }

    public void createResultsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Result");
        View view = getLayoutInflater().inflate(R.layout.results_dialog, null);
        value = view.findViewById(R.id.result);
        Button resultsSubmit = view.findViewById(R.id.results_submit);
        resultsSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resultsDialog.dismiss();
            }
        });
        builder.setView(view);
        resultsDialog = builder.create();
    }

    public void createSyringeDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Syringe Information");
        View view = getLayoutInflater().inflate(R.layout.syringe_dialog, null);
        EditText eName, eVolume, eUnits, eLines;
        eName = view.findViewById(R.id.name);
        eVolume = view.findViewById(R.id.volume);
        eUnits = view.findViewById(R.id.units);
        eLines = view.findViewById(R.id.lines);
        Button submit = view.findViewById(R.id.submit);
        submit.setOnClickListener((x) -> {
                name = eName.getText().toString();
                volume = eVolume.getText().toString();
                units = eUnits.getText().toString();
                numberOfLines = eLines.getText().toString();
                syringeDialog.dismiss();
            }
        );
        builder.setView(view);
        syringeDialog = builder.create();
    }

    public void createLoadingDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("");
        View view = getLayoutInflater().inflate(R.layout.loading_dialog, null);
        builder.setView(view);
        loadingDialog = builder.create();
        loadingDialog.setCancelable(false);
    }

    public void createInfoDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Info");
        View view = getLayoutInflater().inflate(R.layout.info_dialog, null);
        builder.setView(view);
        infoDialog = builder.create();
        Button exit = view.findViewById(R.id.exit_button);
        exit.setOnClickListener((x) -> infoDialog.dismiss());
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        final int modelIndex = modelView.getCheckedItemPosition();
        final String modelString = modelStrings.get(modelIndex);

        try {
            detector = DetectorFactory.getDetector(getAssets(), modelString);
            detectorLines = DetectorFactory.getDetector(getAssets(), Constants.LINE_DETECT_MODEL);
            detectorPlunger = DetectorFactory.getDetector(getAssets(), Constants.PLUNGER_DETECT_MODEL);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.info(e + "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = detector.getInputSize();

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.info(String.format("Camera orientation relative to screen canvas: %d", sensorOrientation));

        LOGGER.info(String.format("Initializing at size %dx%d", previewWidth, previewHeight));
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    protected void updateActiveModel() {
        // Get UI information before delegating to background
        final int modelIndex = modelView.getCheckedItemPosition();
        final int deviceIndex = deviceView.getCheckedItemPosition();
        String threads = threadsTextView.getText().toString().trim();
        final int numThreads = Integer.parseInt(threads);

        handler.post(() -> {
            if (modelIndex == currentModel && deviceIndex == currentDevice
                    && numThreads == currentNumThreads) {
                return;
            }
            currentModel = modelIndex;
            currentDevice = deviceIndex;
            currentNumThreads = numThreads;

            // Disable classifier while updating
            if (detector != null) {
                detector.close();
                detector = null;
            }

            // Lookup names of parameters.
            String modelString = modelStrings.get(modelIndex);
            String device = deviceStrings.get(deviceIndex);

            LOGGER.info("Changing model to " + modelString + " device " + device);

            // Try to load model.

            try {
                detector = DetectorFactory.getDetector(getAssets(), modelString);
                // Customize the interpreter to the type of device we want to use.
                if (detector == null) {
                    return;
                }
            }
            catch(IOException e) {
                e.printStackTrace();
                LOGGER.info(e + "Exception in updateActiveModel()");
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }


            if (device.equals("CPU")) {
                detector.useCPU();
            } else if (device.equals("GPU")) {
                detector.useGpu();
            } else if (device.equals("NNAPI")) {
                detector.useNNAPI();
            }
            detector.setNumThreads(numThreads);

            int cropSize = detector.getInputSize();
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            sensorOrientation, MAINTAIN_ASPECT);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    private String saveToInternalStorage(Bitmap bitmapImage, String filename){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        // path to /data/data/yourapp/app_data/imageDir
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        // Create imageDir
        File mypath=new File(directory,filename);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mypath);
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return directory.getAbsolutePath();
    }

    public Bitmap cropToBoundingBox(Bitmap cropCopyBitmap, Classifier.Recognition temp, String filenameActual, String filenameCrop){
        Bitmap croppedBarrelDetect = null;
        if (temp != null) {
            int left = (int) temp.getLocation().left;
            int top = (int) temp.getLocation().bottom;
            int right = (int) temp.getLocation().right;
            int bottom = (int) temp.getLocation().top;
            croppedBarrelDetect = Bitmap.createBitmap(cropCopyBitmap, left, bottom, right - left, top - bottom);
            System.out.println("Saving cropped detection: " + saveToInternalStorage(croppedBarrelDetect, filenameCrop));
            System.out.println("Saving actual detection: " + saveToInternalStorage(cropCopyBitmap, filenameActual));
        }
        return croppedBarrelDetect;
    }

    public void drawBoundingBox(List<Classifier.Recognition> results, long currTimestamp, String filename){
        trackingOverlay.postInvalidate();
        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);

                result.setLocation(location);
                mappedRecognitions.add(result);
            }
        }

        tracker.trackResults(mappedRecognitions, currTimestamp);
        if (filename != null)
            System.out.println("Saving line count detection: " + saveToInternalStorage(cropCopyBitmap, filename));
        trackingOverlay.postInvalidate();
    }

    public int runDetectionAndCountLines(YoloV5Classifier detector, Bitmap cropCopyBitmap, String type, long currTimestamp){
        int cnt = 0;
        final List<Classifier.Recognition> results = detector.recognizeImage(cropCopyBitmap);
        Classifier.Recognition boundingBox = results.size() == 0 ? null : results.get(0);
        if (boundingBox != null){
            Bitmap croppedImage = cropToBoundingBox(cropCopyBitmap, boundingBox, type + "Actual.jpg", type + "Crop.jpg");
            List<Classifier.Recognition> countLines = detectorLines.recognizeImage(croppedImage);
            System.out.println("Results from counting lines on " + type +  ": " + countLines.size());
            drawBoundingBox(countLines, currTimestamp, type + "lines.jpg");
            cnt = results.size();
        }
        return cnt;
    }

    public int runDetectionAndCountLinesBarrel(YoloV5Classifier detector, Bitmap cropCopyBitmap, String type, long currTimestamp){
        int cnt = -1;
        final List<Classifier.Recognition> results = detector.recognizeImage(cropCopyBitmap);
        Classifier.Recognition boundingBox = results.size() == 0 ? null : results.get(0);
        if (boundingBox != null){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openDialog();
                }
            });
            Bitmap croppedImage = cropToBoundingBox(cropCopyBitmap, boundingBox, type + "Actual.jpg", type + "Crop.jpg");
            List<Classifier.Recognition> countLines = detectorLines.recognizeImage(croppedImage);
            System.out.println("Results from counting lines on " + type +  ": " + countLines.size());
            drawBoundingBox(countLines, currTimestamp, type + "lines.jpg");
            cnt = results.size();
        }
        return cnt;
    }

    @Override
    protected void startPipeline() {
//        openDialog();
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.info("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        int barrelLines = runDetectionAndCountLinesBarrel(detector, cropCopyBitmap, "barrel", currTimestamp);
                        if (barrelLines != -1) {
                            int plungerLines = runDetectionAndCountLines(detectorPlunger, cropCopyBitmap, "plunger", currTimestamp);
                            double eps = 1e-9;
                            double result = (plungerLines / (barrelLines + eps));
                            System.out.println("Total volume ratio is: " + result);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dismissDialog();
                                    resultsDialog(result);
                                }
                            });
                        }
                        computingDetection = false;
                    }
                });
    }
}

