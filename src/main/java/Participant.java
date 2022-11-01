import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import values.Constants;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Participant {

    private static ExecutorService pool = Executors.newFixedThreadPool(4);
    private static Timer timer;
    private static Scanner inputScanner;
    private static Integer port;
    private static String state = Constants.STATE_INIT;
    private static String decision = "";
    private static String data = "";

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
                                            requestAcknowledgement();
                                            break;
                                        case Constants.STATE_GLOBAL_ABORT:
                                            System.err.println("Received GLOBAL ABORT...Aborting!!!\n");
                                            state = Constants.STATE_ABORT;
                                            timer.cancel();
                                            timer.purge();
                                            data = "";
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
