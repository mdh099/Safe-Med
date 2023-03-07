package edu.ucf.safemed;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;

public class FileTest {

    @Test
    public void writeToFile() {
        Syringe syringe1 = new Syringe("a", 20, 10, "mL");
        Syringe syringe2 = new Syringe("b", 10, 5, "cc");
        Syringe syringe3 = new Syringe("c", 100, 1000, "UNITS");
        ArrayList<Object> syringes = new ArrayList<>();
        syringes.add(syringe1);
        syringes.add(syringe2);
        syringes.add(syringe3);

        Gson gson = new Gson();

        Context context = getApplicationContext();
        String json = gson.toJson(syringes);

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("syringes.json", Context.MODE_PRIVATE));
            outputStreamWriter.write(json);
            outputStreamWriter.close();
        }
        catch (IOException e) {
        Log.e("Exception", "File write failed: " + e);
    }
}

    @Test
    public void readFromFile() {

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
        // Breakpoint here to check read in list
    }
}
