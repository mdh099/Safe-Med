package edu.ucf.safemed;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private FloatingActionButton startInferenceButton;

    private TextView value;
    private View progress;

    private List<Syringe> syringeList = new ArrayList<>();
    private String name = null, volume = null, units = null, numberOfLines = null;

    private ListView l;
    private Syringe syringe;

    private ArrayList<String> sL;

    ArrayAdapter<String> arrayAdapter;

    public void openDialog(){
        if (camera2Fragment != null){
            camera2Fragment.onPause();
        }
        else if (legacyFragment != null){
            legacyFragment.onPause();
        }

        loadingDialog.show();
    }

    public void dismissDialog(){
        loadingDialog.dismiss();
    }

    public void resultsDialog(double valueToDisplay){
        runOnUiThread(() -> {
                LOGGER.info("REACHED: " + value);
                // from -1f to 0f
                float desiredPercentage = (float)-(1 - valueToDisplay);
                TranslateAnimation anim = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, -1,
                        Animation.RELATIVE_TO_PARENT, desiredPercentage, Animation.RELATIVE_TO_PARENT,
                        0, Animation.RELATIVE_TO_PARENT, 0);
                anim.setDuration(3000);
                anim.setFillAfter(true);
                if (value != null) {
                    value.setText("" + String.format("%.2f", valueToDisplay * syringe.getVolume()) + " " + syringe.getUnits());
                    LOGGER.info("UPDATING: " + valueToDisplay);
                }
                resultsDialog.show();
                progress.startAnimation(anim);
                progress.setVisibility(View.VISIBLE);
            });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPrefs.getBoolean("firstStart", true)) {
            onFirstLaunch();
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putBoolean("firstStart", false);
            editor.apply();
        }

        TextView textView = new TextView(getApplicationContext());
        textView.setText("Syringe List");

        syringeList = readSyringesFromFile();

        String [] syringes = new String[syringeList.size()];
        sL = new ArrayList<>();
        if (syringes.length == 0) {
            syringe = null;
        }
        else {
            syringe = syringeList.get(0);
        }
        for (int i = 0; i < syringeList.size(); i++) {
            System.out.println(syringeList.get(i));
            String syringeStr = syringeList.get(i).getName();
            System.out.println(syringeStr);
            syringes[i] = syringeStr;
            sL.add(syringeStr);
        }

        runOnUiThread(() -> {
                createSyringeDialog();
                createLoadingDialog();
                createInfoDialog();
                createResultsDialog();

                addSyringeButton = findViewById(R.id.add_syringe_button);
                addSyringeButton.setOnClickListener((view) -> {syringeDialog.show();});

                startInferenceButton = findViewById(R.id.start_inference_button);
                startInferenceButton.setOnClickListener((view) -> { startInferenceButtonClicked = true;});

                infoButton = findViewById(R.id.info_button);
                infoButton.setOnClickListener((view) -> {infoDialog.show();});

                l = (ListView) findViewById(R.id.list);
                l.setClickable(true);

                arrayAdapter = new ArrayAdapter<String>(this,
                        R.layout.layout, R.id.itemTextView, sL);
                l.setAdapter(arrayAdapter);
//                l.addHeaderView(textView);

                l.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        System.out.println("Enter");
                        ArrayList<Syringe> syringeList = readSyringesFromFile();
                        System.out.println("The position is " + position + " The id is " + id + " SyringeList size " + syringeList.size());
                        syringe = syringeList.get(position);
                        System.out.println(syringe.getName() + " " + syringe.getNumLines());

                    }
                });
            }
        );

    }

    public void onFirstLaunch() {
        syringeList.add(new Syringe("3 mL", 30, 3, "mL"));
        syringeList.add(new Syringe("5 mL", 25, 5, "mL"));
        syringeList.add(new Syringe("10 mL", 50, 10, "mL"));
        writeSyringesToFile(syringeList, getApplicationContext());
    }

    public ArrayList<Syringe> readSyringesFromFile() {

        Gson gson = new Gson();
        String ret = "";

        try {
            Context context = getApplicationContext();
            InputStream inputStream = context.openFileInput("syringes.json");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e);
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e);
        }
        Type syringeListType = new TypeToken<ArrayList<Syringe>>(){}.getType();
        ArrayList<Syringe> readSyringes = gson.fromJson(ret, syringeListType);
        return readSyringes;
    }

    public boolean writeSyringesToFile(List<Syringe> syringeList, Context context) {
        Gson gson = new Gson();
        String json = gson.toJson(syringeList);

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("syringes.json", Context.MODE_PRIVATE));
            outputStreamWriter.write(json);
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e);
            return false;
        }
        return true;
    }

    public void createResultsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Result");
        View view = getLayoutInflater().inflate(R.layout.results_dialog, null);
        value = view.findViewById(R.id.result);
        Button resultsSubmit = view.findViewById(R.id.results_submit);
        progress = view.findViewById(R.id.progress);
        resultsSubmit.setOnClickListener(v ->  {
            if (camera2Fragment != null){
                camera2Fragment.onResume();
            }
            else if (legacyFragment != null){
                legacyFragment.onResume();
            }
            resultsDialog.dismiss();
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
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                name = eName.getText().toString();
                volume = eVolume.getText().toString();
                units = eUnits.getText().toString();
                numberOfLines = eLines.getText().toString();

                if (volume == "" || numberOfLines == "") return;

                Syringe newSyringe = new Syringe(name, numberOfLines, volume, units);
                syringeList.add(newSyringe);
                sL.add(name);
                arrayAdapter.notifyDataSetChanged();
                LOGGER.info("Added " + name + " to syringe list.");

                writeSyringesToFile(syringeList, getApplicationContext());
                syringeDialog.dismiss();
            }
        });
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

        Button exit = view.findViewById(R.id.exit_button);
        exit.setOnClickListener((x) -> infoDialog.dismiss());

        builder.setView(view);
        infoDialog = builder.create();
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        try {
            detector = DetectorFactory.getDetector(getAssets(), Constants.BARREL_DETECT_MODEL);
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

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    private String saveToInternalStorage(Bitmap bitmapImage, String filename){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        // path to /data/data/yourapp/app_data/imageDir
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        // Create imageDir
        File myPath=new File(directory,filename);

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(myPath);
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
            LOGGER.info("Saving cropped detection: " + saveToInternalStorage(croppedBarrelDetect, filenameCrop));
            LOGGER.info("Saving actual detection: " + saveToInternalStorage(cropCopyBitmap, filenameActual));
        }

        return croppedBarrelDetect;
    }

    public void drawBoundingBox(List<Classifier.Recognition> results, Bitmap bitmap, long currTimestamp, String filename){
        final Canvas canvas = new Canvas(bitmap);
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

        if (filename != null)
            LOGGER.info("Saving line count detection: " + saveToInternalStorage(bitmap, filename));
    }

    public Bitmap padBitmap(Bitmap bitmap) {
        int paddingLeft = (Constants.input_size - bitmap.getWidth()) / 2;
        int paddingRight = Constants.input_size - (paddingLeft + (Constants.input_size - bitmap.getWidth()) % 2);
        int paddingTop = (Constants.input_size - bitmap.getHeight()) / 2;
        int paddingBottom = Constants.input_size - (paddingTop + (Constants.input_size - bitmap.getHeight()) % 2);
        System.out.println("Stuff " + paddingLeft + " " + paddingRight + " " + paddingTop + " " + paddingBottom + " " + bitmap.getHeight() + " " + bitmap.getWidth());
        Bitmap outputBitmap = Bitmap.createBitmap(Constants.input_size, Constants.input_size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outputBitmap);
        canvas.drawColor(Color.BLUE);
        canvas.drawBitmap(bitmap, null, new Rect(paddingLeft,paddingTop,paddingRight, paddingBottom), null);
        LOGGER.info("Saving padded image: " + saveToInternalStorage(outputBitmap, "paddedImage.jpg"));
        return outputBitmap;
    }

    public ArrayList<Classifier.Recognition> handleOverlap(ArrayList<Classifier.Recognition> lines){
        boolean[] keep = new boolean[lines.size()];
        double threshold = 0.3;
        Arrays.fill(keep, true);
        for (int i = 0; i < lines.size(); i++){
            if (!keep[i]) continue;
            for (int j = i + 1; j < lines.size(); j++) {
                if (!keep[i] || !keep[j]) continue;
                if (detector.box_iou(lines.get(i).getLocation(), lines.get(j).getLocation()) > threshold) {
                    keep[j] = false;
                }
            }
        }
        ArrayList<Classifier.Recognition> temp = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++)
            if (keep[i])
                temp.add(lines.get(i));
        return temp;
    }
//
//    public int countLines(ArrayList<Classifier.Recognition> lines) {
//        Collections.sort(lines, (a, b) -> {
//            return (int)(a.getLocation().bottom - b.getLocation().bottom);
//        });
//
//        int smallestDiff = Integer.MAX_VALUE;
//        for (int i = 1; i < lines.size(); i++){
//            smallestDiff = lines.get(i).getLocation().bottom - lines.get(i - 1).getLocation().bottom;
//        }
//
//        int count = lines.size();
//
//    }

    public int runDetectionAndCountLines(YoloV5Classifier detector, Bitmap cropCopyBitmap, String type, long currTimestamp){
        int cnt = 0;
        final List<Classifier.Recognition> results = detector.recognizeImage(cropCopyBitmap);
        Classifier.Recognition boundingBox = results.size() == 0 ? null : results.get(0);

        if (boundingBox != null){
            Bitmap croppedImage = cropToBoundingBox(cropCopyBitmap, boundingBox, type + "Actual.jpg", type + "Crop.jpg");
            Bitmap padded = padBitmap(croppedImage);
            List<Classifier.Recognition> countLines = handleOverlap(detectorLines.recognizeImage(padded));
            LOGGER.info("Results from counting lines on " + type +  ": " + countLines.size());
            drawBoundingBox(countLines, padded, currTimestamp, type + "lines.jpg");
            cnt = countLines.size();
        }

        return cnt;
    }

    public Bitmap findBarrel(YoloV5Classifier detector, Bitmap cropCopyBitmap){
        final List<Classifier.Recognition> results = detector.recognizeImage(cropCopyBitmap);
        Classifier.Recognition boundingBox = results.size() == 0 ? null : results.get(0);
        Bitmap croppedImage = boundingBox == null ? null : cropToBoundingBox(cropCopyBitmap, boundingBox, "barrelActual.jpg", "barrelCrop.jpg");
        return croppedImage;
    }

    @Override
    protected void startPipeline() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection || !startInferenceButtonClicked) {
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
                () -> {
                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

//                        Bitmap barrelImage = findBarrel(detector, cropCopyBitmap);
//
//                        if (barrelImage != null) {
//                            runOnUiThread(() -> { openDialog(); });
//
//                            int plungerLines = runDetectionAndCountLines(detectorPlunger, padBitmap(barrelImage), "plunger", currTimestamp);
//
//                            double result = (plungerLines / (syringe.getNumLines()));
//                            LOGGER.info("Total volume ratio is: " + result + " " + plungerLines + " " + syringe.getNumLines());
//
//                            runOnUiThread(() -> {
//                                    dismissDialog();
//                                    resultsDialog(result);
//                                }
//                            );
//                        }

                    runOnUiThread(() -> { openDialog(); });
                    List<Classifier.Recognition> countLines = handleOverlap(detectorLines.recognizeImage(cropCopyBitmap));
                    drawBoundingBox(countLines, cropCopyBitmap, currTimestamp,  "generallines.jpg");
                    int plungerLines =  countLines.size();
                    double result = (plungerLines / (syringe.getNumLines()));
                    LOGGER.info("Total volume ratio is: " + result + " " + plungerLines + " " + syringe.getNumLines());

                    runOnUiThread(() -> {
                                dismissDialog();
                                resultsDialog(result);
                            }
                    );

                    computingDetection = false;
                    startInferenceButtonClicked = false;
                });
    }
}

