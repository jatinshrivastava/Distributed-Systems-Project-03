I have neither given nor received unauthorized assistance on this work.

Sign Member 1: Jatin Shrivastava     <br />
Date: 11/01/2022
<br />

Sign Member 2: Aditya Vikram Bhat     <br />
Date: 11/01/2022

# Members of The Project:
### Member 1 Name: Jatin Shrivastava
### UTA ID: 1002032011
<br />

### Member 2 Name: Aditya Vikram Bhat
### UTA ID: 1002014494

# Distributed-Systems-Project-03
#### CSE 5306: Distributed Systems Project 03: Implementation of a 2-phase distributed commit(2PC) protocol and with controlled and randomly injected failures.

# Detailed Instructions to Compile and Run the code
    1. Install the Maven dependencies.
    2. Build the project by clicking on Build > Build Project.
    3. Run Coordinator.java, Client.java, and Participant.java.
    4. You can run multiple instances of Participant.java.
    5. Follow the instructions displayed in the client console.

# How Was The Program Implemented
    The program consists of three primary nodes/ members, namely: Client, Transaction Coordinator (TC), and
    Participant. Client request connection with the TC. TC then sends a prepare message to all the 
    participants. If the participants agree to commit, they vote for a YES, otherwise NO. If any of the
    participant votes for NO, the TC then initiates a global abort.

# Challenges Faced While Working on The Project
    - Setting up socket connection between processes.
    - Detecting socket disconnection within the processes.
    - Managing logs of the nodes.
    - Reading/ writing log files.
    - Managing time out scenarios.

# Learning Outcomes
    - Able to understand how sockets work.
    - Able to establish socket connectivity between multiple machines.
    - Understood how a 2-phase distributed commit protocol works..
    - Understood to how create and update log files.

# Team Members and Roles
    Member 1: Jatin Shrivastava, UTA ID: 1002032011
    Roles: 
        - Project repository setup on Github
        - Project skeleton code setup
        - Socket connection setup
        - Project Documentation

    Member 2: Aditya Vikram Bhat, UTA ID: 1002014494
    Roles: 
        - Creating scenario outlines
        - Managing logs
        - Project Documentation

# References
    - https://stackoverflow.com/questions/17164014/java-lang-classcastexception-java-lang-long-cannot-be-cast-to-java-lang-integer
    - https://beginnersbook.com/2014/01/how-to-write-to-file-in-java-using-bufferedwriter/
    - https://stackoverflow.com/questions/6876266/java-net-connectexception-connection-refused
    - https://stackoverflow.com/questions/17509781/how-to-read-last-line-in-a-text-file-using-java
    - https://www.digitalocean.com/community/tutorials/java-read-file-line-by-line
    - https://martinfowler.com/articles/patterns-of-distributed-systems/two-phase-commit.html
        