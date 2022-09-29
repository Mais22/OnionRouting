/**
 * A class which represents a node.
 */
public class NodeInfo {
    private int port;
    private String pubKey;

    public NodeInfo(int port, String publicKey) {
        this.port = port;
        this.pubKey = publicKey;
    }

    public int getPort() {
        return port;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public void setPort(int port) {
        this.port = port;
    }
}