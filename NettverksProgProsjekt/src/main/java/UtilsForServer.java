import org.json.JSONArray;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A class which contains most of all necessary methods for both Node.java and Sender.java.
 */
class UtilsForServer {
    /**
     * A method which generates a random number between 8000 and 8079 as a port for the node to run on.
     * This method also checks with the directory node class to see if the port is already taken.
     * @return Returns a number between 8000 and 8079 if the port is available and -1 if it is taken.
     * @throws IOException
     * @throws InterruptedException
     */
    public static int getPort() throws IOException, InterruptedException {
        int upperLimit = 8079;
        int lowerLimit = 8000;
        //Can crash if port number is taken, try using Collections.shuffle in server and call on that method
        final int port = (int) Math.floor(Math.random()*(upperLimit-lowerLimit+1)+lowerLimit);
        if(checkIfPortIsTaken(port)) {
            return -1;
        }
        return port;
    }

    /**
     * A method which add a new node to the directory node.
     * @param nodeInf The bytearray containing both the port number and the RSA public key.
     * @throws IOException
     */
    public static void addNewNodeToServer(byte[] nodeInf) throws IOException {
        URL url = new URL("http://localhost:8080/api/nodes/");
        System.out.println("The url is: " + url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");

        con.setDoOutput(true);
        con.getOutputStream().write(nodeInf);
        con.getResponseMessage();
        System.out.println("The node info was sent to the server");
    }

    /**
     * A method which removes a given node from the directory node.
     * @param portNumber The port number of the node to remove.
     * @throws IOException
     */
    public static void removeNodeFromServer(int portNumber) throws IOException {
        URL url = new URL("http://localhost:8080/api/nodes/?portNumber=" + portNumber);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("DELETE");
        con.getResponseMessage();
        System.out.println("The request was sent");
    }

    /**
     * A method which gets the public keys from the directory node.
     * @return Returns a list of byte arrays containing the different public RSA keys. Each element of the list is a key.
     * @throws IOException
     * @throws InterruptedException
     */
    public static List<byte[]> getPublicKeys() throws IOException, InterruptedException {
        URL url = new URL("http://localhost:8080/api/nodes/publickey");
        System.out.println("The url is: " + url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        con.getResponseMessage();
        System.out.println("The request was sent");

        byte[] publicKeys = getFullByteArrayFromStream(con.getInputStream());
        System.out.println("The public keys was received");
        String n = new String(publicKeys, StandardCharsets.UTF_8);
        ArrayList<String> keys = UtilsForServer.splitJSONArray(n);

        List<byte[]> realKeys = new LinkedList<>();
        for (int i = 0; i<keys.size(); i++) {
            byte[] decodedBytes = Base64.getDecoder().decode(keys.get(i));
            realKeys.add(decodedBytes);
        }

        System.out.println("The public keys are sent");
        return realKeys;
    }

    /**
     * A method that checks to see if a given port is already taken. Only ports registered within the directory node are checked.
     * @param portNumber The port number to check.
     * @return Returns true if the port number is taken and false if no node have taken it.
     * @throws IOException
     * @throws InterruptedException
     */
    public static boolean checkIfPortIsTaken(int portNumber) throws IOException, InterruptedException {
        URL url = new URL("http://localhost:8080/api/nodes/nodeList");
        System.out.println("The url is: " + url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        //Sets request method
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        con.getResponseMessage();
        System.out.println("The request was sent");

        byte[] ndsKeys = UtilsForServer.getFullByteArrayFromStream(con.getInputStream());
        String n = new String(ndsKeys, StandardCharsets.UTF_8);
        ArrayList<NodeInfo> nodesAndKeys = UtilsForServer.splitJSONNodeArray(n);
        for(int i = 0; i<nodesAndKeys.size(); i++) {
            if(nodesAndKeys.get(i).getPort() == portNumber) {
                return true;
            }
        }
        return false;
    }

    /**
     * A method which returns a bytearray from an input stream.
     * @param inputStream The input stream you want the array from.
     * @return Returns a bytearray made from the bytes read from the input stream.
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

    /**
     * A method which returns the full byte array from the input stream.
     * @param inputStream The input stream you want the array from.
     * @return Returns a bytearray made from the bytes read from the input stream. This method sleeps for some time to make sure the whole stream is read.
     * @throws IOException
     * @throws InterruptedException
     */
    public static byte[] getFullByteArrayFromStream(InputStream inputStream) throws IOException, InterruptedException {
        byte[] response = null;
        boolean messageRecieved = false;
        while(!messageRecieved) {
            if(!(inputStream == null) && inputStream.available() > 0) {
                response = UtilsForServer.getByteArrayFromInputStream(inputStream);
                TimeUnit.MILLISECONDS.sleep(200);
                System.out.println("Checking the inputstream");
                if(inputStream.available() == 0) {
                    messageRecieved = true;
                }
            }
        }
        return response;
    }

    /**
     * A method which splits a JSON array of strings.
     * @param str The JSON array in the form of a string.
     * @return Returns an arraylist of strings with each separate element from the JSON array.
     */
    public static ArrayList<String> splitJSONArray(String str) {
        JSONArray arr = new JSONArray(str.toString());
        ArrayList<String> publicKeys = new ArrayList<>();
        for(int i = 0; i < arr.length(); i++) {
            publicKeys.add(arr.getString(i));
        }
        return publicKeys;
    }

    /**
     * A method which splits the NodeInfo JSON object received from the directory node.
     * @param str The string containing all the nodes from the server in JSON format.
     * @return Returns an arraylist of node info objects made from the JSON object sent from the server.
     */
    public static ArrayList<NodeInfo> splitJSONNodeArray(String str) {
        JSONArray arr = new JSONArray(str.toString());
        ArrayList<NodeInfo> nodes = new ArrayList<>();
        for(int i = 0; i < arr.length(); i++) {
            arr.getJSONObject(i).getString("pubKey");
            nodes.add(new NodeInfo(arr.getJSONObject(i).getInt("port"), arr.getJSONObject(i).get("pubKey").toString()));
        }
        return nodes;
    }

    /**
     * A method which generates a secret AES key by a given size.
     * @param n The size of the AES key to generate.
     * @return Returns a Secret key object of the AES key.
     * @throws NoSuchAlgorithmException
     */
    public static SecretKey generateSecretKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        SecureRandom secureRandom = new SecureRandom();
        keyGenerator.init(n, secureRandom);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }

    /**
     * A method which generates an IV.
     * @return Returns the IV. The IV is 16 byte in size.
     */
    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    /**
     * A method which encodes a byte array into a Base64 string.
     * @param data The byte array to encode.
     * @return Returns a string of the byte array after the Base64 encoding is done.
     */
    public static String encodeWithBase64(byte[] data){
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * A method which decodes a byte array on the form of base64.
     * @param data The byte array to decode.
     * @return Returns a byte array of the decoded array.
     */
    public static byte[] decodeWithBase64(byte[] data){
        return Base64.getDecoder().decode(data);
    }

    /**
     * A method to combine two byte arrays into one.
     * @param arr1 The first array to combine.
     * @param arr2 The second array to combine.
     * @return Returns a array made from the combination of arr1 and arr2. The new array will have arr1 first and then arr2.
     */
    public static byte[] fuseTwoByteArrays(byte[] arr1, byte[] arr2) {
        byte[] bigArray = new byte[arr1.length+arr2.length];
        for(int i = 0; i < bigArray.length; i++) {
            if(i<arr1.length) {
                bigArray[i] = arr1[i];
            } else {
                bigArray[i] = arr2[i-arr1.length];
            }
        }
        return bigArray;
    }

    /**
     * A method which encrypts a byte array and ands the IV used to the start of the new array.
     * @param algorithm The crypto algorithm to be used.
     * @param sendMessage The array to be encrypted.
     * @param secretKey The secret key used for the encryption.
     * @param iv The IV for the encryption.
     * @return Returns a new byte array of the encrypted array. The new array will also have the iv used added at the start.
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws InvalidKeyException
     */
    public static byte[] encryptAndAddIv(String algorithm, byte[] sendMessage, SecretKey secretKey, IvParameterSpec iv)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        byte[] concatedMessage = UtilsForServer.encrypt(algorithm,sendMessage,secretKey,iv);
        byte[] ivArray = iv.getIV();
        byte[] splitBit = "<//>".getBytes(StandardCharsets.UTF_8);
        ivArray = UtilsForServer.fuseTwoByteArrays(ivArray, splitBit);
        concatedMessage = UtilsForServer.fuseTwoByteArrays(ivArray, concatedMessage);
        return concatedMessage;
    }

    /**
     * A method which decrypts a byte array with the IV at the start.
     * @param algorithm The crypto algorithm to be used.
     * @param receivedMessage The array to be decrypted.
     * @param secretKey The secret key used for the decryption.
     * @return Returns a new byte array of the decrypted array.
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws NoSuchAlgorithmException
     * @throws BadPaddingException
     * @throws InvalidKeyException
     */
    public static byte[] decryptMessageContainingIV(String algorithm, byte[] receivedMessage, SecretKey secretKey)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        List<byte[]> splitMessage = splitByteArray("<//>".getBytes(StandardCharsets.UTF_8),receivedMessage);
        IvParameterSpec iv = new IvParameterSpec(splitMessage.get(0));
        byte[] decryptedMessage = UtilsForServer.decrypt(algorithm,splitMessage.get(1),secretKey,iv);
        return decryptedMessage;
    }

    /**
     * A method which encrypts an array.
     * @param algorithm The crypto algorithm to be used.
     * @param input The array to be encrypted.
     * @param key The secret key used for the encryption.
     * @param iv The IV for the encryption.
     * @return Returns the encrypted array after it has been base64 encrypted.
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     */
    public static byte[] encrypt(String algorithm, byte[] input, SecretKey key, IvParameterSpec iv) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] cipherArray = cipher.doFinal(input);
        cipherArray = Base64.getEncoder().encode(cipherArray);
        return cipherArray;
    }

    /**
     * A method which decrypts an array.
     * @param algorithm The crypto algorithm to be used.
     * @param cipherText The array to be decrypted.
     * @param key The secret key used for the decryption.
     * @param iv The IV for the decryption.
     * @return Returns the decrypted array after it has been base64 decrypted.
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     */
    public static byte[] decrypt(String algorithm, byte[] cipherText, SecretKey key, IvParameterSpec iv) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] dataArray = cipher.doFinal(decodeWithBase64(cipherText));
        return dataArray;
    }

    /**
     * A method which checks a byte array to see if it contains a pattern.
     * @param pattern The pattern to check for.
     * @param input The array to check.
     * @param pos The current position.
     * @return Returns false if the pattern is not in the input and true if it is.
     */
    public static boolean isMatch(byte[] pattern, byte[] input, int pos) {
        for(int i = 0; i<pattern.length; i++) {
            if(pattern[i] != input[pos+i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A method which splits a byte array on a pattern.
     * @param pattern The pattern to split on.
     * @param input The array to split.
     * @return Returns a list of byte arrays each being a part of the original array.
     * Each element in the list is made after the original array was split.
     */
    public static List<byte[]> splitByteArray(byte[] pattern, byte[] input) {
        List<byte[]> list = new LinkedList<>();
        int blockStart = 0;
        int matches = 0;
        for(int i = 0; i<input.length; i++) {
            if(isMatch(pattern, input, i)) {
                list.add(Arrays.copyOfRange(input, blockStart, i));
                blockStart = i+pattern.length;
                i = blockStart;
                matches++;
            }
        }
        list.add(Arrays.copyOfRange(input, blockStart, input.length));
        System.out.println("The length of list is: " + list.size());
        System.out.println(String.format("There was %s matches", matches));
        return list;
    }
}
