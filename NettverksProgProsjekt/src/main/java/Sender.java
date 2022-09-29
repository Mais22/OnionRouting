import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.List;

/**
 * A class which works as the client for the Onion routing service.
 */
public class Sender {
    //A list of the node objects received from the directory node
    static ArrayList<NodeInfo> nodesAndKeys = new ArrayList<>();

    /**
     * A method to get the path across the available nodes. It makes a call to the directory node to get the path.
     * @return Returns a string containing the path from the directory node.
     * The port numbers are differentiated by a "," after the number.
     * @throws IOException
     */
    static String getPath() throws IOException {
        //Sets up a connection to the server
        URL url = new URL("http://localhost:8080/api/nodes/");
        System.out.println("The url is: " + url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        //Sets request method
        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        con.getResponseMessage();
        System.out.println("The request was sent");

        //get the outputstream
        InputStream ip = con.getInputStream();
        BufferedReader br1 = new BufferedReader(new InputStreamReader(ip));

        String response;
        String path = "";
        while ((response = br1.readLine()) != null) {
            path += response;
        }
        System.out.println("The path is: " + path);
        return path;
    }

    /**
     * A method to fill the nodesAndKeys array with the node objects from the directory node.
     * @throws IOException
     * @throws InterruptedException
     */
    static void fillNodesAndKeys() throws IOException, InterruptedException {
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
        nodesAndKeys = UtilsForServer.splitJSONNodeArray(n);
    }

    /**
     * A method to make the public keys from the directory node match with the path.
     * @param path The path which you want the list of keys to match.
     * @param pubKeyList The list of keys which needs correcting.
     * @return Returns a corrected list in which the keys match with the path. Correct key for correct node.
     */
    static List<byte[]> makeKeysAndPathMatch(String path, List<byte[]> pubKeyList) {
        String[] split = path.split(",");
        List<byte[]> correctList = new LinkedList<>();
        for (int i = 0; i< split.length; i++) {
            int port = Integer.valueOf(split[i]);
            for(int j = 0; j< split.length; j++) {
                if(nodesAndKeys.get(j).getPort() == port) {
                    byte[] decodedKey = UtilsForServer.decodeWithBase64(pubKeyList.get(j));
                    correctList.add(decodedKey);
                }
            }
        }
        return correctList;
    }

    /**
     * The main method for the client program. This method works as the entire client.
     * @param args
     */
    public static void main(String[] args) {
        try {
            String algorithm1 = "AES/CFB/NoPadding";
            SecretKey key1 = UtilsForServer.generateSecretKey(256);
            SecretKey key2 = UtilsForServer.generateSecretKey(256);
            IvParameterSpec iv1 = UtilsForServer.generateIv();
            IvParameterSpec iv2 = UtilsForServer.generateIv();

            byte[] text = "Test".getBytes(StandardCharsets.UTF_8);

            byte[] encryptedText1 = UtilsForServer.encryptAndAddIv(algorithm1,text,key1,iv1);
            byte[] encryptedText2 = UtilsForServer.encryptAndAddIv(algorithm1,encryptedText1,key2,iv2);
            byte[] decryptedText2 = UtilsForServer.decryptMessageContainingIV(algorithm1, encryptedText2,key2);
            byte[] decryptedText1 = UtilsForServer.decryptMessageContainingIV(algorithm1, decryptedText2, key1);

            System.out.println(new String(decryptedText1));

            //Gets the port and checks to see if the port is taken by another node.
            int port = UtilsForServer.getPort();
            if(port == -1) {
                throw new IOException("The port is taken, pls start agian");
            }

            //Initializes some necessary variables. The message is the http request which will be sent.
            int keyBitSize = 256;
            String algorithm = "AES/CFB/NoPadding";
            String message = "http://httpbin.org/";

            ServerSocket node = new ServerSocket(port);
            System.out.println("Now running on port: " + port);

            //Gets the path of nodes in which the messages shall be sent over.
            String path = getPath();

            //Splits the path into a string array and extracts the first node. Also makes a connection to the first node.
            String[] splitt = path.split(",");
            int firstNode = Integer.parseInt(splitt[0]);
            Socket outConnection = new Socket("localhost", firstNode);

            //Makes a list of public keys from the directory node. This list is not corrected
            List<byte[]> publicKeysList =  UtilsForServer.getPublicKeys();

            fillNodesAndKeys();

            //Creates a corrected list of public keys from the path and adds them to an arraylist of public key objects.
            List<byte[]> correctList = makeKeysAndPathMatch(path, publicKeysList);
            ArrayList<PublicKey> publicKeys = new ArrayList<>();
            for(int i = 0; i<correctList.size(); i++) {
                PublicKey newKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(correctList.get(i)));
                publicKeys.add(newKey);
            }

            //Creates an arraylist of secret aes keys for later. These will be the aes keys used for the aes encryption.
            ArrayList<SecretKey> secretAESKeys = new ArrayList<>();
            for(int i = 0; i < splitt.length; i++) {
                SecretKey key = UtilsForServer.generateSecretKey(keyBitSize);
                secretAESKeys.add(key);
            }

            //Checks to see if the path is longer than one node.
            if(splitt.length > 1) {
                for (int i = 0; i <= splitt.length; i++) {
                    String sentMessage = "";

                    //Creates a list of one time ivs which will be used for encryption and decryption of aes.
                    ArrayList<IvParameterSpec> ivsForKeys = new ArrayList<>();
                    for(int j = 0; j < splitt.length; j++) {
                        IvParameterSpec iv = UtilsForServer.generateIv();
                        ivsForKeys.add(iv);
                    }

                    //checks to see which message should be sent.
                    if(i == splitt.length) {
                        //This is the http request for the last node.
                        sentMessage = message;
                        byte[] sendMessage = sentMessage.getBytes(StandardCharsets.UTF_8);

                        //Encrypts the message.
                        for(int j = splitt.length-1; j>-1; j--) {
                            sendMessage = UtilsForServer.encryptAndAddIv(algorithm,sendMessage,secretAESKeys.get(j),ivsForKeys.get(j));
                        }

                        //Sends the message into the node path.
                        outConnection.getOutputStream().write(sendMessage);
                        outConnection.getOutputStream().flush();
                        System.out.println("The message is sent, waiting for response.");

                        //Awaits the response from the last node.
                        byte[] byteResponse = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());
                        System.out.println("Response received");

                        //Check to see that we have actually gotten the correct response.
                        if(!java.util.Arrays.equals(byteResponse, "OK".getBytes(StandardCharsets.UTF_8))) {
                            //Decrypts the response message.
                            for(int j = 0; j<splitt.length; j++) {
                                byteResponse = UtilsForServer.decryptMessageContainingIV(algorithm,byteResponse,secretAESKeys.get(j));
                            }

                            //Converts the response to a string and writes it to a html file. Then opens the html file.
                            String response = new String(byteResponse, StandardCharsets.UTF_8);
                            System.out.println("The response has been recieved");

                            File f = new File("file.html");
                            BufferedWriter bw = new BufferedWriter(new FileWriter(f.getName()), response.length());
                            bw.write(response);
                            bw.close();
                            Desktop.getDesktop().browse(f.toURI());
                        }
                    }
                    else if(i > 0) {
                        //We are currently not on the first or last message to send.

                        //Checks to see if the message is for the last node or not.
                        if(i != splitt.length-1) {
                            sentMessage = splitt[i+1] + "<//>";
                            byte[] secretKey = secretAESKeys.get(i).getEncoded();
                            String keyEncoded = UtilsForServer.encodeWithBase64(secretKey);
                            sentMessage += keyEncoded;
                        } else {
                            sentMessage = "LastNode";
                            byte[] secretKey = secretAESKeys.get(i).getEncoded();
                            String keyEncoded = UtilsForServer.encodeWithBase64(secretKey);
                            sentMessage += keyEncoded;
                        }
                        byte[] sentMessageBytes = sentMessage.getBytes(StandardCharsets.UTF_8);

                        //RSA encrypts the aes key message.
                        Cipher encryptCipher = Cipher.getInstance("RSA");
                        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKeys.get(i));
                        byte[] sendMessage = encryptCipher.doFinal(sentMessageBytes);
                        System.out.println("The message is RSA encrypted.");

                        //Aes encrypts the message.
                        int j = i-1;
                        do {
                            sendMessage = UtilsForServer.encryptAndAddIv(algorithm,sendMessage,secretAESKeys.get(j),ivsForKeys.get(j));
                            j--;
                        } while (j > -1);

                        //Sends the message to the other nodes, and awaits the response.
                        outConnection.getOutputStream().write(sendMessage);
                        outConnection.getOutputStream().flush();
                        System.out.println("The message is sent, waiting for response.");

                        byte[] byteResponse = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());
                        System.out.println("Response received");

                        if(!java.util.Arrays.equals(byteResponse, "OK".getBytes(StandardCharsets.UTF_8))) {
                            System.out.println("Something is very wrong.");
                        }
                    } else {
                        //We are on the first message to send

                        //Prepares the message with the aes key.
                        sentMessage = splitt[i+1] + "<//>";
                        byte[] secretKey = secretAESKeys.get(i).getEncoded();
                        String keyEncoded = UtilsForServer.encodeWithBase64(secretKey);
                        sentMessage += keyEncoded;
                        byte[] sentMessageBytes = sentMessage.getBytes(StandardCharsets.UTF_8);

                        //RSA encrypts the message.
                        Cipher encryptCipher = Cipher.getInstance("RSA");
                        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKeys.get(i));
                        byte[] sendMessage = encryptCipher.doFinal(sentMessageBytes);
                        System.out.println("The message is RSA encrypted.");

                        //Sends the message to the first node and awaits the response.
                        outConnection.getOutputStream().write(sendMessage);
                        outConnection.getOutputStream().flush();
                        System.out.println("The message is sent, waiting for response.");

                        byte[] byteResponse = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());
                        System.out.println("Response received");

                        if(!java.util.Arrays.equals(byteResponse, "OK".getBytes(StandardCharsets.UTF_8))) {
                            System.out.println("Something is very wrong.");
                        }
                    }
                }
            } else {
                //If there is only one node in the path

                //Prepares the message with the aes key.
                String firstMessage = "";
                byte[] secretKey = secretAESKeys.get(0).getEncoded();
                String keyEncoded = UtilsForServer.encodeWithBase64(secretKey);
                firstMessage = keyEncoded;
                byte[] sentMessageBytes = firstMessage.getBytes(StandardCharsets.UTF_8);

                //RSA encrypts the message.
                Cipher encryptCipher = Cipher.getInstance("RSA");
                encryptCipher.init(Cipher.ENCRYPT_MODE, publicKeys.get(0));
                byte[] sendMessage = encryptCipher.doFinal(sentMessageBytes);
                System.out.println("The message is encrypted.");

                //Sends the message to the first node and awaits the response.
                outConnection.getOutputStream().write(sendMessage);
                outConnection.getOutputStream().flush();

                byte[] byteResponse = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());

                if(!java.util.Arrays.equals(byteResponse, "OK".getBytes(StandardCharsets.UTF_8))) {
                    System.out.println("Something is very wrong.");
                }

                //Generates an iv and aes encrypts the message.
                IvParameterSpec iv = UtilsForServer.generateIv();
                sendMessage = UtilsForServer.encryptAndAddIv(algorithm,sendMessage,secretAESKeys.get(0),iv);

                //Sends the encrypted message.
                outConnection.getOutputStream().write(sendMessage);
                outConnection.getOutputStream().flush();

                //Receives the message and decrypts it.
                byteResponse = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());
                byteResponse = UtilsForServer.decryptMessageContainingIV(algorithm,byteResponse,secretAESKeys.get(0));

                String response = new String(byteResponse, StandardCharsets.UTF_8);
                System.out.println("The response has been recieved");

                //Writes the response to a html file and opens said file.
                File f = new File("file.html");
                BufferedWriter bw = new BufferedWriter(new FileWriter(f.getName()), response.length());
                bw.write(response);
                bw.close();
                Desktop.getDesktop().browse(f.toURI());
            }
        } catch (UnknownHostException ue) {
            System.out.println("The name of the servermachine is wrong");
        } catch (SocketException se) {
            System.out.println("The connection was suddenly terminated, pls try agian.");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException nf) {
          System.out.println("The path is blank, pls start some nodes.");
        } catch (Exception e) {
            System.out.println("Ops there was an error.");
            e.printStackTrace();
        }
    }
}