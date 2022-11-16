import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import values.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Participant {

    private static ExecutorService pool = Executors.newFixedThreadPool(4);
    private static Timer timer;
    private static Scanner inputScanner;
    private static Integer port;
    private static String state = Constants.STATE_INIT;
    private static String decision = "";
    private static String data = "";
    private static FileReader fr;
    private static FileWriter fw;
    private static BufferedReader br;
    private static BufferedWriter bw;

    public static void main(String[] args) {
        try {
            System.out.println("Starting Participant");

            Thread helperThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToCoordinator();
                }
            });

            helperThread.start();

            port = getValidPort(9000);

            timer = new Timer();

            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    if(state.equals(Constants.STATE_INIT)) {
                        System.err.println("Did not receive PREPARE message from the coordinator...Initiating Local Abort!!!\n");
                        state = Constants.STATE_LOCAL_ABORT;
                    }
                }
            };

            timer.schedule(timerTask, 35000);

            initListeningClient(port);

        } catch (Exception e) {
            System.err.println("Participant exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static int getValidPort(int port) {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
        } catch (Exception e) {
            return getValidPort(port+1);
        }

        try {
            serverSocket.close();
        } catch (Exception e) {

        }

        return port;
    }

    private static void initListeningClient(int port) throws IOException {
        File f = new File("Participant_" +port+".log");
        f.createNewFile();

        fr = new FileReader(f);
        br = new BufferedReader(fr);

        fw = new FileWriter(f, true);
        bw = new BufferedWriter(fw);

        ServerSocket serverSocket = new ServerSocket(port);
        int count = 4;
        while (count != 0) {
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!serverSocket.isClosed()) {
                            Socket socket = serverSocket.accept();
                            BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            while (!socket.isClosed()) {
                                if (inputStream.read() == -1) {
                                    socket.close();
                                }
                                String input = (String) inputStream.readLine();
                                if (input != null && !input.trim().equals("")) {
                                    JSONParser jsonParser = new JSONParser();
                                    JSONObject jsonObject = (JSONObject) jsonParser.parse(input.substring(1));
                                    String senderName = (String) jsonObject.get(Constants.sender);
                                    String messageType = (String) jsonObject.get(Constants.type);
                                    String message = (String) jsonObject.get(Constants.message);

                                    switch (messageType) {
                                        case Constants.STATE_GLOBAL_COMMIT:
                                            System.err.println("Received GLOBAL COMMIT...Committing!!!\n");
                                            state = Constants.STATE_COMMIT;
                                            timer.cancel();
                                            timer.purge();
                                            data = "";

                                            JSONObject jsonObj0 = new JSONObject();
                                            jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                            jsonObj0.put(Constants.type, Constants.STATE_GLOBAL_COMMIT);
                                            jsonObj0.put(Constants.sender, "Coordinator");

                                            try {
                                                bw.write(jsonObj0.toJSONString());
                                                bw.newLine();
                                                bw.flush();
                                            } catch (Exception e) {
                                                System.err.println("error in writing data");
                                            }

                                            requestAcknowledgement();
                                            break;
                                        case Constants.STATE_GLOBAL_ABORT:
                                            System.err.println("Received GLOBAL ABORT...Aborting!!!\n");
                                            state = Constants.STATE_ABORT;
                                            timer.cancel();
                                            timer.purge();
                                            data = "";

                                            JSONObject jsonObj1 = new JSONObject();
                                            jsonObj1.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                            jsonObj1.put(Constants.type, Constants.STATE_GLOBAL_ABORT);
                                            jsonObj1.put(Constants.sender, "Coordinator");

                                            try {
                                                bw.write(jsonObj1.toJSONString());
                                                bw.newLine();
                                                bw.flush();
                                            } catch (Exception e) {
                                                System.err.println("error in writing data");
                                            }

                                            break;
                                        case Constants.STATE_REQUEST_VOTE:
                                            data = message;
                                            if (state == Constants.STATE_LOCAL_ABORT) {
                                                System.err.println("Participant was in Local Abort stage... Voting NO!!!\n");
                                                voteNo();
                                            } else {
                                                System.out.println("Received VOTING REQUEST...\n");
                                                timer.cancel();
                                                timer.purge();

                                                JSONObject jsonObj2 = new JSONObject();
                                                jsonObj2.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                                jsonObj2.put(Constants.type, Constants.PREPARE);
                                                jsonObj2.put(Constants.sender, "Coordinator");

                                                try {
                                                    bw.write(jsonObj2.toJSONString());
                                                    bw.newLine();
                                                    bw.flush();
                                                } catch (Exception e) {
                                                    System.err.println("error in writing data");
                                                }

                                                getParticipantVote();
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                            inputStream.close();
                        }
                    } catch (IOException e) {
                        System.err.println("IOException:");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } catch (ParseException e) {
                        System.err.println("Unable to parse Json String");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });
            count--;
            pool.execute(listeningThread);
//            listeningThread.start();
        }

        readLog();
    }

    private static void readLog(){
        String lastLine = "null", line = "";
        String corLastLine = "null", corLine = "";

        try {
            BufferedReader corInput = new BufferedReader(new FileReader("coordinator.log"));
            while ((corLine = corInput.readLine()) != null) {
                corLastLine = corLine;
            }
        } catch (Exception e) {

        }

        try {
            BufferedReader input = new BufferedReader(new FileReader("Participant_"+port+".log"));
            while ((line = input.readLine()) != null) {
                lastLine = line;
            }
        } catch (Exception e) {

        }
        if (!lastLine.isEmpty()) {
            try {
                JSONParser jsonParser = new JSONParser();
                JSONObject jsonObject = (JSONObject) jsonParser.parse(lastLine);
                JSONObject corJsonObject = (JSONObject) jsonParser.parse(corLastLine);
                if (corJsonObject.get(Constants.type).equals(Constants.STATE_COMMIT)) {
                    ArrayList<Long> sendList = (ArrayList<Long>) corJsonObject.get(Constants.sent);
                    AtomicBoolean portPresent = new AtomicBoolean(false);
                    sendList.forEach((element) -> {
                        if (element.toString().equals(port.toString())) {
                            portPresent.set(true);
                        }
                    });
                    if (portPresent.get()) {
                        System.out.println("Node failed without receiving commit. Requesting commit again!");
                        requestCommitMessage();
                    } else if (jsonObject.get(Constants.type).equals(Constants.STATE_GLOBAL_COMMIT)) {
                        System.out.println("Node failed without acknowledgement");
                        requestAcknowledgement();
                    }

                }
            } catch (Exception e) {
                    System.out.println("exception!!!!!");
//                    e.printStackTrace();
            }
        }

    }

    private static void requestCommitMessage() {
        Thread listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(Constants.localhostURL, Constants.coordinator_listening_port);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(Constants.sender, "Participant_"+port);
                    jsonObject.put(Constants.receiver, "Coordinator");
                    jsonObject.put(Constants.message, "");
                    jsonObject.put(Constants.type, Constants.STATE_REQUEST_COMMIT);
                    output.writeUTF(jsonObject.toJSONString());

                    output.flush();
                    output.close();
                    socket.close();

                    state = Constants.STATE_READY;

                    timer = new Timer();
                    TimerTask timerTask2 = new TimerTask() {
                        @Override
                        public void run() {
                            if(!decision.equals(Constants.STATE_GLOBAL_COMMIT) || !decision.equals(Constants.STATE_GLOBAL_ABORT)) {
                                System.err.println("Did not receive decision from the coordinator...Initiating Local Abort!!!\n");
                                state = Constants.STATE_LOCAL_ABORT;
                            }
                        }
                    };

                    timer.schedule(timerTask2, 60000);

                } catch (UnknownHostException e) {
                    System.err.println("UnknownHostException:");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    System.err.println("IOException:");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
        listeningThread.start();

        JSONObject jsonObj0 = new JSONObject();
        jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
        jsonObj0.put(Constants.type, Constants.STATE_GLOBAL_COMMIT);
        jsonObj0.put(Constants.sender, "Coordinator");

        try {
            bw.write(jsonObj0.toJSONString());
            bw.newLine();
            bw.flush();
        } catch (Exception e) {
            System.err.println("error in writing data");
        }
    }

    private static void requestAcknowledgement() {
        System.out.println("01. Enter 1 to send acknowledgement of commit\n");
        Scanner inputScanner = new Scanner(System.in);
        String input = inputScanner.nextLine();
        switch (input) {
            case "1": {
                Thread listeningThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Socket socket = new Socket(Constants.localhostURL, Constants.coordinator_listening_port);
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Participant_"+port);
                            jsonObject.put(Constants.receiver, "Coordinator");
                            jsonObject.put(Constants.message, "");
                            jsonObject.put(Constants.type, Constants.ACKNOWLEDGEMENT);
                            output.writeUTF(jsonObject.toJSONString());

                            output.flush();
                            output.close();
                            socket.close();

                            state = Constants.STATE_READY;

                            timer = new Timer();
                            TimerTask timerTask2 = new TimerTask() {
                                @Override
                                public void run() {
                                    if(!decision.equals(Constants.STATE_GLOBAL_COMMIT) || !decision.equals(Constants.STATE_GLOBAL_ABORT)) {
                                        System.err.println("Did not receive decision from the coordinator...Initiating Local Abort!!!\n");
                                        state = Constants.STATE_LOCAL_ABORT;
                                    }
                                }
                            };

                            timer.schedule(timerTask2, 60000);

                        } catch (UnknownHostException e) {
                            System.err.println("UnknownHostException:");
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            System.err.println("IOException:");
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                });
                listeningThread.start();

                JSONObject jsonObj0 = new JSONObject();
                jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
                jsonObj0.put(Constants.type, Constants.ACKNOWLEDGEMENT);
                jsonObj0.put(Constants.sender, "Participant_"+port);

                try {
                    bw.write(jsonObj0.toJSONString());
                    bw.newLine();
                    bw.flush();
                } catch (Exception e) {
                    System.err.println("error in writing data");
                }

                break;
            }
            default:
                System.out.println("Invalid input! Please try again!");
                requestAcknowledgement();
                break;
        }
    }

    private static void getParticipantVote() {
        System.out.println("01. Enter 1 to vote for YES. \n02. Enter 2 to vote for NO.\n");
        inputScanner = new Scanner(System.in);
        String input = inputScanner.nextLine();
        switch (input) {
            case "1": {
                System.out.println("Voting YES!!!");
                voteYes();
                break;
            }
            case "2": {
                System.err.println("Voting NO!!!");
                voteNo();
                break;
            }
            default:
                System.out.println("Invalid input! Please try again!");
                getParticipantVote();
                break;
        }
    }

    private static void voteYes() {
        if (state.equals(Constants.STATE_READY) || (data.trim().isEmpty() || data.equals(""))) {
            System.err.println("Can't vote right now!!!");
        } else {
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = new Socket(Constants.localhostURL, Constants.coordinator_listening_port);
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(Constants.sender, "Participant_"+port);
                        jsonObject.put(Constants.receiver, "Coordinator");
                        jsonObject.put(Constants.message, "");
                        jsonObject.put(Constants.type, Constants.VOTE_YES);
                        output.writeUTF(jsonObject.toJSONString());

                        output.flush();
                        output.close();
                        socket.close();

                        state = Constants.STATE_READY;

                        timer = new Timer();
                        TimerTask timerTask2 = new TimerTask() {
                            @Override
                            public void run() {
                                if(!decision.equals(Constants.STATE_GLOBAL_COMMIT) || !decision.equals(Constants.STATE_GLOBAL_ABORT)) {
                                    System.err.println("Did not receive decision from the coordinator...Initiating Local Abort!!!\n");
                                    state = Constants.STATE_LOCAL_ABORT;
                                }
                            }
                        };

                        timer.schedule(timerTask2, 60000);

                    } catch (UnknownHostException e) {
                        System.err.println("UnknownHostException:");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        System.err.println("IOException:");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });
            listeningThread.start();

            JSONObject jsonObj0 = new JSONObject();
            jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
            jsonObj0.put(Constants.type, Constants.VOTE_YES);
            jsonObj0.put(Constants.sender, "Participant_"+port);

            try {
                bw.write(jsonObj0.toJSONString());
                bw.newLine();
                bw.flush();
            } catch (Exception e) {
                System.err.println("error in writing data");
            }
        }
    }

    private static void voteNo() {
        if (!(data.trim().isEmpty() || data.equals(""))) {
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = new Socket(Constants.localhostURL, Constants.coordinator_listening_port);
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(Constants.sender, "Participant_" + port);
                        jsonObject.put(Constants.receiver, "Coordinator");
                        jsonObject.put(Constants.message, "");
                        jsonObject.put(Constants.type, Constants.VOTE_NO);
                        output.writeUTF(jsonObject.toJSONString());

                        output.flush();
                        output.close();
                        socket.close();

                        state = Constants.STATE_ABORT;
                    } catch (UnknownHostException e) {
                        System.err.println("UnknownHostException:");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        System.err.println("IOException:");
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });
            listeningThread.start();

            JSONObject jsonObj0 = new JSONObject();
            jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
            jsonObj0.put(Constants.type, Constants.VOTE_NO);
            jsonObj0.put(Constants.sender, "Participant_"+port);

            try {
                bw.write(jsonObj0.toJSONString());
                bw.newLine();
                bw.flush();
            } catch (Exception e) {
                System.err.println("error in writing data");
            }

        } else {
            System.err.println("Can't vote right now!!!");
        }
    }

    private static void connectToCoordinator() {
        Thread listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(Constants.localhostURL, Constants.coordinator_listening_port);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(Constants.sender, "Participant_"+port);
                    jsonObject.put(Constants.receiver, "Coordinator");
                    jsonObject.put(Constants.message, "");
                    jsonObject.put(Constants.type, Constants.connect);
                    output.writeUTF(jsonObject.toJSONString());

                    output.flush();
                    output.close();
//                    socket.close();

                } catch (UnknownHostException e) {
                    System.err.println("UnknownHostException:");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    System.err.println("IOException:");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
        listeningThread.start();
    }
}
