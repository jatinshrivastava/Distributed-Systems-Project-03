import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import values.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Coordinator {

    private static ExecutorService pool = Executors.newFixedThreadPool(4);
    private static FileReader fr;
    private static FileWriter fw;
    private static BufferedReader br;
    private static BufferedWriter bw;
    private static Timer timer;
    private static String state = Constants.STATE_INIT;

    private static HashMap<String, String> votes;
    private static HashMap<String, Integer> participantsList;
    public static void main(String[] args) {
        try {
            System.out.println("Starting Coordinator...\n");

//            Thread helperThread = new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    sendPrepareMessage();
//                }
//            });
//
//            helperThread.start();

            votes = new HashMap<>();
            participantsList = new HashMap<>();
            initListeningClient();

            System.out.println("Coordinator Started\n");

        } catch (Exception e) {
            System.err.println("Coordinator exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static void initiateConnection() {
        System.out.println("01. Enter 1 to send prepare message to the participants \n");
        Scanner inputScanner = new Scanner(System.in);
        String input = inputScanner.nextLine();
        switch (input) {
            case "1": {
                System.out.println("Sending Prepare message to the participants");
                JSONArray sentList = new JSONArray();
                JSONArray unSentList = new JSONArray();
                JSONObject participants = new JSONObject();
                for (String participant: participantsList.keySet()) {
                    participants.put(participant, participantsList.get(participant));
                    Thread senderThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Socket socket = new Socket(Constants.localhostURL, participantsList.get(participant));
                                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put(Constants.message, "test");
                                jsonObject.put(Constants.type, Constants.STATE_REQUEST_VOTE);
                                output.writeUTF(jsonObject.toJSONString());

                                output.flush();
                                output.close();
                                socket.close();
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
                    sentList.add(participantsList.get(participant));
                    senderThread.start();
                }

                JSONObject jsonObject = new JSONObject();
                jsonObject.put(Constants.timeStamp, Instant.now().toEpochMilli());
                jsonObject.put(Constants.type, Constants.PREPARE);
                jsonObject.put(Constants.sent, sentList);
                jsonObject.put(Constants.unSent, unSentList);
                jsonObject.put(Constants.participants, participants);

                try {
                    bw.write(jsonObject.toJSONString());
                    bw.newLine();
                    bw.flush();
                } catch (Exception e) {
                    System.err.println("error in writing data");
                }

                state = Constants.STATE_WAITING;

                timer = new Timer();

                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        int voteCount = 0;
                        for (String participant: votes.keySet()) {
                            if(votes.get(participant).equals(Constants.STATE_COMMIT)) {
                                voteCount++;
                            } else {
                                break;
                            }
                        }

                        if (voteCount != votes.size()) {
                            System.err.println("Time Out...Not all participants voted.\n");
                            System.err.println("Initiating Global Abort!!!\n");

                            for(String participant: votes.keySet()) {
                                votes.put(participant, "");
                            }

                            selectParticipants(true);
//                    sendAbort();

                            state = Constants.STATE_ABORT;
                        }
                    }
                };

                timer.schedule(timerTask, 45000);
                break;
            }
            default:
                System.out.println("Invalid input! Please try again!");
                initiateConnection();
                break;
        }
    }

    private static void initListeningClient() throws IOException {
        File f = new File("coordinator.log");
        f.createNewFile();

        fr = new FileReader(f);
        br = new BufferedReader(fr);

        fw = new FileWriter(f, true);
        bw = new BufferedWriter(fw);

        ServerSocket serverSocket = new ServerSocket(Constants.coordinator_listening_port);
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
                                        case Constants.connect:
                                            if (!senderName.equalsIgnoreCase("client")) {
                                                votes.put(senderName, "");
                                                Integer portNumber = Integer.parseInt(senderName.split("_")[1]);
                                                participantsList.put(senderName, portNumber);
                                            }
                                            System.out.println(senderName + " Connected.");
                                            break;
                                        case Constants.disconnect:
                                            System.out.println(senderName + " Disconnected.");
                                            participantsList.remove(senderName);
                                            socket.close();
                                            break;
                                        case Constants.CLIENT_INIT:
                                            System.out.println(senderName + " Connected to client...\n");
                                            initiateConnection();
                                            break;
                                        case Constants.VOTE_NO:
                                            System.out.println(senderName + " voted No...Initiating Global Abort");

                                            selectParticipants(true);
//                                            sendAbort();

                                            JSONObject jsonObj0 = new JSONObject();
                                            jsonObj0.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                            jsonObj0.put(Constants.type, Constants.VOTE_NO);
                                            jsonObj0.put(Constants.sender, participantsList.get(senderName));

                                            try {
                                                bw.write(jsonObj0.toJSONString());
                                                bw.newLine();
                                                bw.flush();
                                            } catch (Exception e) {
                                                System.err.println("error in writing data");
                                            }

                                            for(String participant: votes.keySet()) {
                                                votes.put(participant, "");
                                            }

                                            break;
                                        case Constants.VOTE_YES:
                                            System.out.println(senderName + " voted Yes.");

                                            votes.put(senderName, Constants.VOTE_YES);

                                            JSONObject jsonObj1 = new JSONObject();
                                            jsonObj1.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                            jsonObj1.put(Constants.type, Constants.VOTE_YES);
                                            jsonObj1.put(Constants.sender, participantsList.get(senderName));

                                            try {
                                                bw.write(jsonObj1.toJSONString());
                                                bw.newLine();
                                                bw.flush();
                                            } catch (Exception e) {
                                                System.err.println("error in writing data");
                                            }

                                            int counter = 0;
                                            for (String participant: votes.keySet()) {
                                                if(votes.get(participant).equals(Constants.VOTE_YES)) {
                                                    counter++;
                                                } else {
                                                    break;
                                                }
                                            }

                                            System.out.println("votes is: "+ votes);
                                            System.out.println("counter is: "+ counter);
                                            if (counter == votes.size()) {
                                                System.err.println("All participants voted Yes...Initiating Global Commit!!!\n");
                                                selectParticipants(false);
//                                                sendCommit();

                                                for(String participant: votes.keySet()) {
                                                    votes.put(participant, "");
                                                }
                                            }

                                            break;
                                        case Constants.ACKNOWLEDGEMENT:
                                            System.out.println(senderName + " sent Acknowledgement");

                                            JSONObject jsonObj2 = new JSONObject();
                                            jsonObj2.put(Constants.timeStamp, Instant.now().toEpochMilli());
                                            jsonObj2.put(Constants.type, Constants.ACKNOWLEDGEMENT);
                                            jsonObj2.put(Constants.sender, participantsList.get(senderName));

                                            try {
                                                bw.write(jsonObj2.toJSONString());
                                                bw.newLine();
                                                bw.flush();
                                            } catch (Exception e) {
                                                System.err.println("error in writing data");
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
        List<String> stringList = new ArrayList<String>();
        boolean lastCommitFound = false;

        try {
            String string;
            do {
                string = br.readLine();
                stringList.add(string);
            } while (string != null);
        } catch (Exception e) {

        }
        if (!stringList.isEmpty()) {
            for (int i=stringList.size()-1; i>=0; i--) {
                try {
                    JSONParser jsonParser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) jsonParser.parse(stringList.get(i));
                    if (jsonObject.get(Constants.type).equals(Constants.STATE_COMMIT)) {
                        lastCommitFound = true;
                        System.out.println("Last commit object was: "+ jsonObject);
                        ArrayList<Long> unSentList = (ArrayList<Long>) jsonObject.get(Constants.unSent);
                        ArrayList<Long> sendList = (ArrayList<Long>) jsonObject.get(Constants.sent);
                        JSONObject participantList = (JSONObject) jsonObject.get(Constants.participants);
                        HashMap<String, Integer> pMap = new HashMap<>();
                        for (Object participant: participantList.keySet()) {
                            Integer port = Integer.valueOf(participantList.get(participant).toString());
                            pMap.put(participant.toString(), port);
                        }
                        if (!unSentList.isEmpty()) {
//                            deleteLastLog();
                            sendRemainingCommit(unSentList, sendList, participantList);
                        }
                    }
                } catch (Exception e) {
//                    System.out.println("exception!!!!!");
//                    e.printStackTrace();
                }

                if (lastCommitFound) {
                    break;
                }
            }
        }

    }

    private static void selectParticipants(boolean isAbort) {
        System.out.println("01. Enter 1 to send the message to one participant only. \n02. Enter 2 to send the message to all the participants.");
        Scanner inputScanner = new Scanner(System.in);
        String input = inputScanner.nextLine();
        switch (input) {
            case "1": {
                if (isAbort) {
                    sendAbort(true);
                } else {
                    sendCommit(true);
                }
                break;
            }
            case "2": {
                if (isAbort) {
                    sendAbort(false);
                } else {
                    sendCommit(false);
                }
                break;
            }
            default:
                System.out.println("Invalid input! Please try again!");
                selectParticipants(isAbort);
                break;
        }
    }

    private static void sendCommit(boolean onlyOne) {
        JSONArray sentList = new JSONArray();
        JSONArray unSentList = new JSONArray();
        JSONObject participants = new JSONObject();
        for (String participant: participantsList.keySet()) {
            participants.put(participant, participantsList.get(participant));
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = new Socket(Constants.localhostURL, participantsList.get(participant));
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(Constants.sender, "Coordinator");
                        jsonObject.put(Constants.receiver, participant);
                        jsonObject.put(Constants.message, "");
                        jsonObject.put(Constants.type, Constants.STATE_GLOBAL_COMMIT);
                        output.writeUTF(jsonObject.toJSONString());

                        output.flush();
                        output.close();
                        socket.close();
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
//            listeningThread.start();
            if (onlyOne) {
                if (sentList.isEmpty()) {
                    listeningThread.start();
                    sentList.add(participantsList.get(participant));
                } else {
                    unSentList.add(participantsList.get(participant));
                }
            } else {
                listeningThread.start();
                sentList.add(participantsList.get(participant));
            }
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Constants.timeStamp, Instant.now().toEpochMilli());
        jsonObject.put(Constants.type, Constants.STATE_COMMIT);
        jsonObject.put(Constants.sent, sentList);
        jsonObject.put(Constants.unSent, unSentList);
        jsonObject.put(Constants.participants, participants);

        try {
            bw.write(jsonObject.toJSONString());
            bw.newLine();
            bw.flush();
        } catch (Exception e) {
            System.err.println("error in writing data");
        }

        state = Constants.STATE_COMMIT;
        timer.cancel();
        timer.purge();
    }

    private static void sendAbort(boolean onlyOne) {
        for (String participant: participantsList.keySet()) {
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = new Socket(Constants.localhostURL, participantsList.get(participant));
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(Constants.sender, "Coordinator");
                        jsonObject.put(Constants.receiver, participant);
                        jsonObject.put(Constants.message, "");
                        jsonObject.put(Constants.type, Constants.STATE_GLOBAL_ABORT);
                        output.writeUTF(jsonObject.toJSONString());

                        output.flush();
                        output.close();
                        socket.close();
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

        state = Constants.STATE_ABORT;
        timer.cancel();
        timer.purge();
    }

    private static void sendRemainingCommit(ArrayList<Long> uList, ArrayList<Long> sList, HashMap<String, Integer> pList) {
        System.out.println("Sending remaining commit to: " + uList);
        participantsList = pList;

        JSONArray sentList = new JSONArray();
        JSONObject participantsObj = new JSONObject();
        for (String participant: participantsList.keySet()) {
            participantsObj.put(participant, participantsList.get(participant));
        }

        for (int i = 0; i < uList.size(); i++) {
            int finalI = i;
            String participantName = "Participant_"+ uList.get(finalI);
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = new Socket(Constants.localhostURL, Math.toIntExact(uList.get(finalI)));
                        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(Constants.sender, "Coordinator");
                        jsonObject.put(Constants.receiver, participantName);
                        jsonObject.put(Constants.message, "");
                        jsonObject.put(Constants.type, Constants.STATE_GLOBAL_COMMIT);
                        output.writeUTF(jsonObject.toJSONString());

                        output.flush();
                        output.close();
                        socket.close();
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
            System.out.println("Sent to: "+ participantName);
            listeningThread.start();
            sentList.add(Math.toIntExact(uList.get(finalI)));
        }

        JSONObject jsonObject = new JSONObject();
        JSONArray unSentList = new JSONArray();

        sList.forEach((element) -> {
            sentList.add(element);
        });

        jsonObject.put(Constants.timeStamp, Instant.now().toEpochMilli());
        jsonObject.put(Constants.type, Constants.STATE_COMMIT);
        jsonObject.put(Constants.sent, sentList);
        jsonObject.put(Constants.unSent, unSentList);
        jsonObject.put(Constants.participants, participantsObj);
        try {
            bw.write(jsonObject.toJSONString());
            bw.newLine();
            bw.flush();
        } catch (Exception e) {
            System.err.println("error in writing data");
        }

        state = Constants.STATE_COMMIT;
        timer.cancel();
        timer.purge();
    }

    private static void deleteLastLog() {
        try {
            RandomAccessFile file = new RandomAccessFile("coordinator.log", "rw");

            long length = file.length() - 1;
            byte b;
            do {
                length -= 1;
                file.seek(length);
                b = file.readByte();
            } while (b != 10);
            file.setLength(length + 1);
            file.close();
        } catch (Exception e) {
            System.err.println("File access exception:");
            e.printStackTrace();
        }
    }
}
