package com.example.DirectoryNode.Controller;
import com.example.DirectoryNode.DTO.NodeInfo;
import com.example.DirectoryNode.Service.DirectoryNodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A controller for the directory node.
 */
@RequestMapping("/api/nodes")
@RestController
@CrossOrigin
public class DirectoryNodeController {
    Logger logger = LoggerFactory.getLogger(DirectoryNodeController.class);
    ArrayList<NodeInfo> nodeList = new ArrayList<>();

    /**
     * A method which adds a new node to the directory node register.
     * @param inputStream The input stream to check.
     */
    @PostMapping("/")
    public void addNewNodeToTheList(InputStream inputStream) {
        try {
            //Receives the node info from the input stream.
            byte[] nodeInf = null;
            boolean messageRecieved = false;
            while(!messageRecieved) {
                if(!(inputStream == null) && inputStream.available() > 0) {
                    nodeInf = DirectoryNodeService.getByteArrayFromInputStream(inputStream);
                    TimeUnit.MILLISECONDS.sleep(200);
                    logger.info("Checking the inputstream");
                    if(inputStream.available() == 0) {
                        messageRecieved = true;
                    }
                }
            }
            //Splits the node info into port and pub key.
            ByteBuffer bb = ByteBuffer.wrap(nodeInf);
            byte[] portNumberByte = new byte[4];
            byte[] publicKey = new byte[nodeInf.length-portNumberByte.length];
            bb.get(portNumberByte, 0, portNumberByte.length);
            bb.get(publicKey, 0, publicKey.length);
            String s = new String(portNumberByte, StandardCharsets.UTF_8);
            int portNumber = Integer.valueOf(s);
            byte[] publicKeyEncoded = Base64.getEncoder().encode(publicKey);
            String stringPubKeyEncoded = new String(publicKeyEncoded);

            //Checks to see if the port is already taken by another node.
            if(nodeList.stream().anyMatch(n -> n.getPort() == portNumber)) {
                logger.info(String.format("The node %s was already in use", portNumber));
            } else {
                NodeInfo node = new NodeInfo(portNumber, stringPubKeyEncoded);
                nodeList.add(node);
                logger.info(String.format("The new portnumber added is: %s", portNumber));
                logger.info(String.format("The public key is added"));
            }
        } catch (IOException io) {
            io.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * A method to remove a node from the directory node register.
     * @param portNumber The port number for the node to be removed.
     */
    @DeleteMapping("/")
    public void removeNodeFromTheList(@RequestParam(name = "portNumber") int portNumber) {
        for (int i = 0; i < nodeList.size(); i++) {
            if(nodeList.get(i).getPort() == portNumber) {
                nodeList.remove(i);
            }
        }
        logger.info(String.format("The node with portnumber: %s has been removed from the list", portNumber));
    }

    /**
     * A method to create a path across all registered nodes.
     * @return Returns the created path.
     */
    @GetMapping("/")
    public String makePath() {
        ArrayList<Integer> path = new ArrayList<>();
        for(int i = 0; i < nodeList.size(); i++) {
            path.add(nodeList.get(i).getPort());
        }
        Collections.shuffle(path);
        AtomicReference<String> thePath = new AtomicReference<>("");
        path.forEach(e -> thePath.set(thePath + e.toString() + ","));
        logger.info(String.format("The path is: %s", thePath));
        return thePath.toString();
    }

    /**
     * A method to send the list of nodes registered.
     * @return Returns a new array of the nodes registered.
     */
    @GetMapping("/nodeList")
    public ArrayList<NodeInfo> getNodeList() {
        ArrayList<NodeInfo> n = new ArrayList<>();
        for (int i = 0; i<nodeList.size(); i++) {
            n.add(nodeList.get(i));
        }
        return n;
    }

    /**
     * A method which sends the public keys registered.
     * @return Returns a new list of the public keys.
     */
    @GetMapping("/publickey")
    public List<byte[]> getPublicKeys() {
        List<byte[]> b = new LinkedList<>();
        for(int i = 0; i<nodeList.size(); i++) {
            b.add(nodeList.get(i).getPubKey().getBytes(StandardCharsets.UTF_8));
        }
        return b;
    }
}