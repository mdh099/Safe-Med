package javaObjects;

import android.content.ContextWrapper;

import com.google.gson.Gson;

import edu.ucf.safemed.Syringe;

import org.junit.Test;

public class GsonTest {
    @Test
    public void objectToJson() {
        Syringe syringe = new Syringe("test", 20, 10, "tests");
        Gson gson = new Gson();

        String json = gson.toJson(syringe);
        // System.out.println(json);
    }

    @Test
    public void jsonToObject() {
        Gson gson = new Gson();
        Syringe syringe = new Syringe("Buster",10,5, "mL");
        String json = gson.toJson(syringe);
        Syringe newsyringe = gson.fromJson(json, Syringe.class);

        // System.out.println(newsyringe);
    }
}
