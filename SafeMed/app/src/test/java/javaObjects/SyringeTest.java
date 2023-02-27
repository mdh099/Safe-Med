package javaObjects;
import edu.ucf.safemed.Syringe;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyringeTest {
    @Test
    public void syringeCreation() {
        Syringe syringe = new Syringe(20, 10);

        assertEquals(syringe.name, "10ml (20 lines)");
    }
}
