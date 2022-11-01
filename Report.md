I have neither given nor received unauthorized assistance on this work.

Sign Member 1: Jatin Shrivastava     <br />
Date: 10/02/2022
<br />

# Members of The Project:
### Member 1 Name: Jatin Shrivastava
### UTA ID: 1002032011
<br />

# Distributed-Systems-Project-01
#### CSE 5306: Distributed Systems Project 02: Vector clock implementation in an n-node distributed system.

# Detailed Instructions to Compile and Run the code
    1. Install the Maven dependencies.
    2. Build the project by clicking on Build > Build Project.
    3. Run Machine1.java, Machine2.java, and Machine3.java.
    4. Follow the instructions displayed in the client console.

# How Was The Program Implemented
    The program consists of three machines, namely: Machine1, Machine2, and Machine3. It also contains 
    a values package which holds a Constants class. This class contatins some final String value used
    throughout the machine classes. Each machine (process) acts as both, a server, and a client, in
    order to send and receive messages. Since this project consists of 3 machines. Each machine opens
    up two listening threads for other machings to connect to. Every time a message is sent or received,
    the internal clock is incremented by 1. During the sending of message, the machine's internal vector
    clock is also sent as message along with other metadata in a json string format. This json string is
    then parsed and the respective values are obtained on the other machine.

# Challenges Faced While Working on The Project
    - Setting up socket connection between processes.
    - Detecting socket disconnection within the processes.
    - Synchronizing the vector clocks among the processes.
    - Enabling multi process message listening within each process.

# Learning Outcomes
    - Able to understand how sockets work.
    - Able to establish socket connectivity between multiple machines.
    - Understood how vector clock works.
    - Understood how to create threads for listening and sending ports within a process.

# Team Members and Roles
    Member 1: Jatin Shrivastava, UTA ID: 1002032011
    Roles: 
        - Project repository setup on Github
        - Project skeleton code setup
        - Socket connection setup
        - Setting up vector clock for each process
        - Project Documentation

# References
    - https://stackoverflow.com/questions/72815154/sending-and-receiving-messages-in-java
    - https://www.geeksforgeeks.org/vector-clocks-in-distributed-systems/
    - https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
    - https://docs.oracle.com/javase/tutorial/networking/sockets/index.html
    - https://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html
    - https://www.javatpoint.com/socket-programming
    - https://www.geeksforgeeks.org/socket-programming-in-java/
    - https://www.youtube.com/watch?v=ZIzoesrHHQo
        