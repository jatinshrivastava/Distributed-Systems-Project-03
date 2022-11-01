import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import values.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Machine1 {

    private static ExecutorService pool = Executors.newFixedThreadPool(4);
    static long internalClockCount = 0;
    static long machine2ClockCount = 0;
    static long machine3ClockCount = 0;

    public static void main(String[] args) {

        try {
            System.out.println("Hello Machine1 User!");
            System.out.println("Setting Up Listening Port");

            Thread helperThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    getClientToSendMessage();
                }
            });

            helperThread.start();
            initListeningClient();

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static void initSendingClient() {
        Thread senderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket("localhost", 6000);
                    DataOutputStream outputStream = new DataOutputStream(s.getOutputStream());
                    outputStream.writeUTF("Hiiiiiii From Client 1");
                    outputStream.flush();
                    outputStream.close();
                    s.close();
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

    private static void connectToMachine(Integer portNumber, String machineName) {
        Thread listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Connecting to " + machineName);
//                    Socket s = new Socket("localhost", portNumber);


                    Socket socket = new Socket(Constants.localhostURL, portNumber);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(Constants.sender, "Machine 1");
                    jsonObject.put(Constants.receiver, machineName);
                    jsonObject.put(Constants.message, "");
                    jsonObject.put(Constants.type, Constants.connect);
                    output.writeUTF(jsonObject.toJSONString());

                    output.flush();
                    output.close();
                    socket = new Socket(Constants.localhostURL, portNumber);
                    output = new DataOutputStream(socket.getOutputStream());
                    bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                    System.out.println("Type a message and press ENTER key to send the message:");
                    while(true){
                        String input = bufferedReader.readLine();

                        //           receiveResponse(port);
                        if(input.equalsIgnoreCase("exit")) {
                            jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Machine 1");
                            jsonObject.put(Constants.receiver, machineName);
                            jsonObject.put(Constants.message, "");
                            jsonObject.put(Constants.type, Constants.disconnect);
                            output.writeUTF(jsonObject.toJSONString());
                            output.flush();
                            output.close();
                            getClientToSendMessage();
                            break;
                        } else {
                            System.out.println("Machine 1 vector clock BEFORE is: " + "<" + internalClockCount + ", " + machine2ClockCount + ", " + machine3ClockCount + ">");
                            internalClockCount++;
                            jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Machine 1");
                            jsonObject.put(Constants.receiver, machineName);
                            jsonObject.put(Constants.message, input);
                            jsonObject.put(Constants.type, Constants.message);
                            jsonObject.put(Constants.machine1VectorClock, internalClockCount);
                            jsonObject.put(Constants.machine2VectorClock, machine2ClockCount);
                            jsonObject.put(Constants.machine3VectorClock, machine3ClockCount);
                            output.writeUTF(jsonObject.toJSONString());
                            System.out.println("Machine 1 vector clock AFTER is: " + "<" + internalClockCount + ", " + machine2ClockCount + ", " + machine3ClockCount + ">");
                            output.flush();
                            output.close();
                            socket = new Socket(Constants.localhostURL, portNumber);
                            output = new DataOutputStream(socket.getOutputStream());
                            bufferedReader = new BufferedReader(new InputStreamReader(System.in));
//                            System.out.println("message sent " + input );
                        }
                        //            if(output.writeUTF(input)){}
                    }
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

    private static void initListeningClient() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Constants.machine_1_listening_port);
        int count = 4;
        while (count != 0) {
            Thread listeningThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!serverSocket.isClosed()) {
                            Socket socket = serverSocket.accept();
                            BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                            while (!socket.isClosed()) {
                                if (inputStream.read() == -1) {
//                                System.out.println("Machine Disconnected!");
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
                                            System.out.println(senderName + " Connected.");
                                            break;
                                        case Constants.disconnect:
                                            System.out.println(senderName + " Disconnected.");
                                            socket.close();
                                            break;
                                        case Constants.message:
                                            long machine2Clock = (long) jsonObject.get(Constants.machine2VectorClock);
                                            long machine3Clock = (long) jsonObject.get(Constants.machine3VectorClock);
                                            System.out.println("Machine 1 vector clock BEFORE is: " + "<" + internalClockCount + ", " + machine2ClockCount + ", " + machine3ClockCount + ">");
                                            internalClockCount++;
                                            machine2ClockCount = machine2Clock;
                                            machine3ClockCount = machine3Clock;
                                            System.out.println(senderName + " sent: " + message);
                                            System.out.println("Machine 1 vector clock AFTER is: " + "<" + internalClockCount + ", " + machine2ClockCount + ", " + machine3ClockCount + ">");
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

    private static void getClientToSendMessage() {
        System.out.println("01. Enter 2 to send a message to Machine 2. \n02. Enter 3 to send a message to Machine 3.");
        Scanner inputScanner = new Scanner(System.in);
        String input = inputScanner.nextLine();
        switch (input) {
            case "2": {
                connectToMachine(Constants.machine_2_listening_port, "Machine 2");
                break;
            }
            case "3": {
//                connectToMachine(Constants.machine_3_listening_port, "Machine 3");
                break;
            }
            default:
                System.out.println("Invalid input! Please try again!");
                getClientToSendMessage();
                break;
        }
    }
}
