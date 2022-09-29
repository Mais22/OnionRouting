package com.example.DirectoryNode.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class which contains help-methods for the directory node.
 */
public class DirectoryNodeService {
    /**
     * A method the get a byte array from an input stream.
     * @param inputStream The input stream to get the array from.
     * @return Returns a new byte array made from the input array.
     * @throws IOException
     */
    public static byte[] getByteArrayFromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        while(inputStream.available() > 0 && (bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
            b.write(buffer, 0, bytesRead);
        }
        byte[] res = b.toByteArray();
        return res;
    }
}