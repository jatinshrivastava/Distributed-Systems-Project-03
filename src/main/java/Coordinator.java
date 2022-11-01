import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import values.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator {

    private static ExecutorService pool = Executors.newFixedThreadPool(4);
    private static FileReader fr;
    private static FileWriter fw;
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

            initListeningClient();

            votes = new HashMap<>();
            participantsList = new HashMap<>();

            System.out.println("Coordinator Started\n");

        } catch (Exception e) {
            System.err.println("Coordinator exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static void sendPrepareMessage() {
        System.out.println("Sending Prepare message to the participants");
        for (String participant: participantsList.keySet()) {
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
            senderThread.start();
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
    }
    private static void initListeningClient() throws IOException {
        File f = new File("coordinator.log");
        f.createNewFile();

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
                                            socket.close();
                                            break;
                                        case Constants.CLIENT_COMMIT:
                                            System.out.println(senderName + " requested to commit...Initiating Prepare!!!\n");
                                            sendPrepareMessage();
                                            break;
                                        case Constants.STATE_ABORT:
                                            System.out.println(senderName + " voted Abort...Initiating Global Abort");

                                            selectParticipants(true);
//                                            sendAbort();

                                            for(String participant: votes.keySet()) {
                                                votes.put(participant, "");
                                            }

                                            break;
                                        case Constants.STATE_COMMIT:
                                            System.out.println(senderName + " voted Commit.");

                                            votes.put(senderName, Constants.STATE_COMMIT);

                                            int counter = 0;
                                            for (String participant: votes.keySet()) {
                                                if(votes.get(participant).equals(Constants.STATE_COMMIT)) {
                                                    counter++;
                                                } else {
                                                    break;
                                                }
                                            }

                                            if (counter == votes.size()) {
                                                System.err.println("All participants voted to Commit...Initiating Global Commit!!!\n");
                                                selectParticipants(false);
//                                                sendCommit();
                                            }

                                            for(String participant: votes.keySet()) {
                                                votes.put(participant, "");
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
            listeningThread.start();
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
}
