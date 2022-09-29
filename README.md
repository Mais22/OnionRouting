#CryingOnion - One onion routing implementation

#Introduction

CryingOnion is an onion routing implementation that lets a client send HTTP requests through the help of a network of nodes.
Encryption is used to ensure the security of the data sent. This way the privacy of users can be maintained to a greater 
degree. 

This implementation is made in java and contains 3 main components. These are: The client, nodes and a directory node. 
The client is used to send the requests to the node network. This network consists of all the nodes. Each node is a small 
server which either relays incoming data or fires received requests. Lastly, the directory node is a server where the data 
on active nodes are kept. The client relies on being able to connect to the directory node to function, since the client 
has no other way to access necessary node data.

# Implemented functionality

* Sending GET requests for an HTTP-site and saving said site to a file.
* Back and forth encryption using AES-256-CFB and RSA.

# Future work
* Capability to send GET requests for HTTPS-sites.
* 80% unit testing (Currently 0%)
* Increased safety and efficiency for usage of CryingOnion.

# Dependencies
CryingOnion has currently just one dependency. 

* [org.json](https://mvnrepository.com/artifact/org.json/json)
  * JSON framework used by the client and nodes, simply for convenience.

All the rest of the code is written using Java's built-in libraries.

# Installation
* Step 1: Download the .zip project folder.
* Step 2: Extract the files at a desired destination.
* Step 3: Open up your code editor of choice.
* Step 4: Navigate into the \DirectoryNode folder.
* Step 5: Find the DirectoryNodeApplication.java file and run it.
* Step 6: Then navigate into the \NettverksProgProsjekt folder.
* Step 7: Find the Node.java file and run multiple instances of it (recomended 3).
* Step 8: In the same folder find the Sender.java file and run it. 
* Extra: 
  * If you wish to change the default http request open the Sender.java file and navigate down to the message string. 
  Just change the string from http://httpbin.org/ to something else. 

# Testing
Due to time limitations version 1.0 of CryingOnion has no unit testing. Additionally, temporary sout statements have 
been removed. This issue has been marked and will be improved for version 1.1.

# API documentation
The javadoc for the project can be found within the folder named javadoc. 

# Remarks
Since this is version 1.0 of CryingOnion some improvements are due and easy usability is somewhat lacking. The developers
behind CryingOnion apologise profoundly and promise to improve the program before the next update. Until then, please 
enjoy what there currently is of CryingOnion.