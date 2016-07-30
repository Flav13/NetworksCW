/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Flavius
 */
public class NewEmptyJUnitTest {

    public NewEmptyJUnitTest() {
    }

    @Test
    public void hello() {

        byte[] pie = new byte[512];
        int i = 0;
        pie[0]=48;
        assertTrue(pie[0]!=0);
    }
}
