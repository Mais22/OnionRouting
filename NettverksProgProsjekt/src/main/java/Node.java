import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A class which works as the nodes for the Onion routing.
 */
public class Node {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(20)).build();

    public static void main(String[] args) {
         try {
             String serverSide = "localhost";
             //Gets the port and checks to see if the port is taken by another node.
             int port = UtilsForServer.getPort();
             if(port == -1) {
                 throw new IOException("The port is taken, pls start agian");
             }

             //Generates the RSA keys used in the encryption of AES keys.
             KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
             generator.initialize(2048);
             KeyPair pair = generator.generateKeyPair();

             PrivateKey privateKey = pair.getPrivate();
             PublicKey publicKey = pair.getPublic();
             System.out.println("The rsa keys are made");
             String algorithm = "AES/CFB/NoPadding";

             ServerSocket node = new ServerSocket(port);
             System.out.println("Now running on port: " + port);

             //Adds the node to the directory node after a port is taken.
             String sPort = String.valueOf(port);
             byte[] portArray = sPort.getBytes(StandardCharsets.UTF_8);
             byte[] pubKeyEncoded = publicKey.getEncoded();
             byte[] nodeinf = UtilsForServer.fuseTwoByteArrays(portArray, pubKeyEncoded);
             UtilsForServer.addNewNodeToServer(nodeinf);
             System.out.println("New node added to server.");

             Socket inputConnection = node.accept();
             System.out.println("The socket is made.");

             //Awaits a message and decrypts it with the private key.
             byte[] msg = UtilsForServer.getFullByteArrayFromStream(inputConnection.getInputStream());
             Cipher decryptCipher = Cipher.getInstance("RSA");
             decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

             byte[] decryptedRSAMessage = decryptCipher.doFinal(msg);

             ByteBuffer bb = ByteBuffer.wrap(decryptedRSAMessage);
             byte[] nxtNode = new byte[8];
             byte[] restOfMessage = new byte[decryptedRSAMessage.length-nxtNode.length];
             bb.get(nxtNode, 0, nxtNode.length);
             bb.get(restOfMessage, 0, restOfMessage.length);
             System.out.println("A message has been received");

             //Checks the first 8 bytes for any occurrences of the split bit <//>.
             // If it is we know that we are not on the last node in the path.
             String n = new String(nxtNode, StandardCharsets.UTF_8);
             if(n.contains("<//>")) {
                 //Extracts the next node from the message.
                 String[] splittMessage = n.split("<//>");
                 int nextNode = Integer.parseInt(splittMessage[0]);
                 System.out.println("The port is " + nextNode);

                 UtilsForServer.removeNodeFromServer(port);
                 System.out.println("The node has been removed from the server.");

                 Socket outConnection = new Socket(serverSide, nextNode);
                 System.out.println("A connection to the next node is made");

                 //Decodes the rest of the message to receive the aes key.
                 restOfMessage = UtilsForServer.decodeWithBase64(restOfMessage);
                 SecretKey secretKey = new SecretKeySpec(restOfMessage,"AES");

                 //Send the message ok to signal that the node connection is ready.
                 inputConnection.getOutputStream().write("OK".getBytes(StandardCharsets.UTF_8));
                 inputConnection.getOutputStream().flush();

                 //Loops through all messages which potentially can be received.
                while(true) {
                    //decrypts all messages from input connection
                    byte[] messageToSend = UtilsForServer.getFullByteArrayFromStream(inputConnection.getInputStream());

                    byte[] decryptedAESMessage = UtilsForServer.decryptMessageContainingIV(algorithm, messageToSend,secretKey);

                    outConnection.getOutputStream().write(decryptedAESMessage);
                    outConnection.getOutputStream().flush();
                    System.out.println("The message has been sent to port: " + nextNode);

                    //Encrypts all non OK responses from output connection
                    byte[] response = UtilsForServer.getFullByteArrayFromStream(outConnection.getInputStream());

                    //If response is not ok, a http request response has been received.
                    if(!java.util.Arrays.equals(response, "OK".getBytes(StandardCharsets.UTF_8))) {
                        IvParameterSpec iv = UtilsForServer.generateIv();
                        byte[] responseEncrypted = UtilsForServer.encryptAndAddIv(algorithm,response,secretKey,iv);
                        System.out.println("A response has been received.");
                        inputConnection.getOutputStream().write(responseEncrypted);
                        inputConnection.getOutputStream().flush();
                        break;
                    }
                    System.out.println("A response has been received.");
                    inputConnection.getOutputStream().write(response);
                    inputConnection.getOutputStream().flush();
                }
             } else {
                 //The last Node

                 //Decodes the rest of the message to get the secret aes key.
                 restOfMessage = UtilsForServer.decodeWithBase64(restOfMessage);
                 SecretKey secretKey = new SecretKeySpec(restOfMessage,"AES");

                 UtilsForServer.removeNodeFromServer(port);
                 System.out.println("The node has been removed from the server.");

                 System.out.println("A message was received and the message was: " + n);

                 inputConnection.getOutputStream().write("OK".getBytes(StandardCharsets.UTF_8));
                 inputConnection.getOutputStream().flush();

                 byte[] messageToSend = UtilsForServer.getFullByteArrayFromStream(inputConnection.getInputStream());

                 //Decrypts the request message and creates the http request.
                 byte[] decryptedAESMessage = UtilsForServer.decryptMessageContainingIV(algorithm, messageToSend,secretKey);

                 String requestMessage = new String(decryptedAESMessage,StandardCharsets.UTF_8);

                 HttpRequest request = HttpRequest.newBuilder()
                         .GET().uri(URI.create(requestMessage)).build();

                 CompletableFuture<HttpResponse<String>> response1 = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

                 String res = response1.thenApply(HttpResponse::body).get(5, TimeUnit.SECONDS);

                 //Encrypts the responses to the request and sends it back.
                 byte[] response = res.getBytes(StandardCharsets.UTF_8);

                 IvParameterSpec iv = UtilsForServer.generateIv();
                 byte[] responseEncrypted = UtilsForServer.encryptAndAddIv(algorithm,response,secretKey,iv);

                 inputConnection.getOutputStream().write(responseEncrypted);
                 inputConnection.getOutputStream().flush();
                 System.out.println("The response has been sent back");
             }
         } catch (UnknownHostException ue) {
             System.out.println("The name of the servermachine is wrong");
         } catch (SocketException se) {
             System.out.println("The connection was suddenly terminated, pls try agian.");
         } catch (IOException e) {
             e.printStackTrace();
         } catch (Exception e) {
             System.out.println("Ops there was an error.");
             e.printStackTrace();
         }
     }
}