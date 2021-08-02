package org.hisp.dhis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.hisp.dhis.category.IntegrationTest;
import org.hisp.dhis.response.Response;
import org.hisp.dhis.response.Status;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category( IntegrationTest.class )
public class DataStoreApiTest
{
    @Test
    public void testCrud()
    {
        Dhis2 dhis2 = new Dhis2( TestFixture.DEFAULT_CONFIG );

        // Save

        Fruit banana = new Fruit( "Banana", "Yellow" );
        Fruit grape = new Fruit( "Grape", "Green" );

        Response rA = dhis2.saveDataStoreEntry( "fruits", "banana", banana );
        Response rB = dhis2.saveDataStoreEntry( "fruits", "grape", grape );

        System.out.println( rA );
        assertEquals( Status.OK, rA.getStatus() );
        assertEquals( Status.OK, rB.getStatus() );

        banana = dhis2.getDataStoreEntry( "fruits", "banana", Fruit.class );
        grape = dhis2.getDataStoreEntry( "fruits", "grape", Fruit.class );

        assertNotNull( banana );
        assertEquals( "Banana", banana.getName() );
        assertEquals( "Yellow", banana.getColor() );

        assertNotNull( grape );
        assertEquals( "Grape", grape.getName() );
        assertEquals( "Green", grape.getColor() );

        // Get namespaces

        List<String> namespaces = dhis2.getDataStoreNamespaces();

        assertNotNull( namespaces );
        assertFalse( namespaces.isEmpty() );

        // Get keys

        List<String> keys = dhis2.getDataStoreKeys( "fruits" );

        assertNotNull( keys );
        assertEquals( 2, keys.size() );

        // Update

        Fruit redBanana = new Fruit( "Banana", "Red" );

        Response rC = dhis2.updateDataStoreEntry( "fruits", "banana", redBanana );

        assertEquals( Status.OK, rC.getStatus() );

        redBanana = dhis2.getDataStoreEntry( "fruits", "banana", Fruit.class );

        assertNotNull( redBanana );
        assertEquals( "Red", redBanana.getColor() );

        // Remove

        Response rD = dhis2.removeDataStoreEntry( "fruits", "banana" );
        Response rE = dhis2.removeDataStoreEntry( "fruits", "grape" );

        assertEquals( Status.OK, rD.getStatus() );
        assertEquals( Status.OK, rE.getStatus() );
    }
}