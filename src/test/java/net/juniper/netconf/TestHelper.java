package net.juniper.netconf;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;

class TestHelper {

      static File getSampleFile(String fileName) throws FileNotFoundException {
        URL sampleFileUri = ClassLoader.getSystemClassLoader()
                .getResource(fileName);
        if (sampleFileUri == null) {
            throw new FileNotFoundException(String.format("Could not find file %s", fileName));
        }
        return new File(sampleFileUri.getFile());
     }
}

