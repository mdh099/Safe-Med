package edu.ucf.safemed;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Syringe {
    private UUID uuid;
    private String name;
    private double volume;
    private String volumeUnit;
    private double numLines;

    public Syringe(String name, String numLinesString, String volumeString, String volumeUnit) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.numLines = Double.parseDouble(numLinesString);
        this.volume = Double.parseDouble(volumeString);
        this.volumeUnit = volumeUnit;
    }

    public Syringe(String name, double numLines, double volume, String volumeUnit) {
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.numLines = numLines;
        this.volume = volume;
        this.volumeUnit = volumeUnit;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getNumLines() {
        return numLines;
    }

    public void setNumLines(double numLines) {
        this.numLines = numLines;
    }

    public List<Syringe> readFromFile(Context context){
        Gson gson = new Gson();
        String ret = "";

        try {
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
        return gson.fromJson(ret, syringeListType);
    }

    public boolean writeToFile(List<Syringe> syringeList, Context context) {
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
}
