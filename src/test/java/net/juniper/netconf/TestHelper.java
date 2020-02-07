package net.juniper.netconf;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

class TestHelper {

      static File getSampleFile(String fileName) throws FileNotFoundException {
        URL sampleFileUri = ClassLoader.getSystemClassLoader()
                .getResource(fileName);
        File sampleFile;
        if (sampleFileUri == null) {
            throw new FileNotFoundException(String.format("Could not find file %s", fileName));
        }
        return new File(sampleFileUri.getFile());
     }
}

