GemmaRose Ragozzine
Programming Assignment 2

My program includes the following 8 classes:
Link.java : a data stucture that represents a link. It includes the cost (weight) of the link and the direct origin of the link (aka the starting node).

UserInputListener.java : This class is how the program listens for user commands. It uses its own Datagram socket to listen to user input and then sends the verified user input to the bfClient.

bfClient.java : This class holds the meat of the project, it is the central class that keeps everything functioning. This class handles the part 1 bellman-ford algorithm operation primarily in the methods changeCost, changeCostUpdateDV, and bellmanFordUpdate. It also handles the transfer mechanism of part 2. 

bfClientServer.java : This class handles the heartbeat mechanism for the nodes. In 3*timeout, if this class has not heard from one of it's neighbors, it considers the link down.

Host.java : This class represents a host with an IP-address and a port number.

RouteUpdate : This class is the class that handles the periodical route updates that are sent to each direct neighbor during each timeout period. It uses poison-reverse to ensure the functionality of the bellman-ford algorithm. 

bfClientTester : The tester class to start the program.

SavedDV : This class is a data structure I made to simplify the bellman ford update. It represends a saved distance vector that a bfClient has received from one of its neighbors to keep track of the shortest paths. 


How to compile code:
I included a make file, so to compile the code just run:
make

To run the compiled code, run:
java bfClientTester {config file}
For example, java bfClientTester Test1Client1.txt will run the code using a confige file named "test1client1.txt"

The config file must be written as directed in the assignment instructions.

I have included some test cases that I used while I was building this project. Both of those test cases show high functionality for parts 1 and 2. Part 3 (bonus) was not attempted. As for part 2, I am able to fragment large images into smaller packets and then rebuild the photo once the destination is made, however the rebuilding process is buggy so the image does not come out 100% perfect.

Also note: During file transfer, since I broke the image down to many small packets (each 1024 bytes) I felt that it would be overwhelming to print the source/destination/next hop for each individual packet. Instead, it prints this information when the last packet in the file is received. This is a small change that I can demonstrate in office hours if need be. 

The program was tested on a clic machine and functions.






