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

public class Client {

    public static void main(String[] args) {

        try {
            System.out.println("Hello User!");
            System.out.println("Setting Up Listening Port");

            Thread helperThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToCoordinator(Constants.coordinator_listening_port,  "Coordinator");
                }
            });

            helperThread.start();
            initListeningClient();

        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private static void connectToCoordinator(Integer portNumber, String machineName) {
        Thread listeningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Connecting to " + machineName + "...");


                    Socket socket = new Socket(Constants.localhostURL, portNumber);
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(Constants.sender, "Client");
                    jsonObject.put(Constants.receiver, machineName);
                    jsonObject.put(Constants.message, "");
                    jsonObject.put(Constants.type, Constants.connect);
                    output.writeUTF(jsonObject.toJSONString());

                    output.flush();
                    output.close();

                    System.out.println("Connected!\n");

                    socket = new Socket(Constants.localhostURL, portNumber);
                    output = new DataOutputStream(socket.getOutputStream());
                    bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                    System.out.println("Type a message and press ENTER key to send the message:");
                    while(true){
                        String input = bufferedReader.readLine();

                        if(input.equalsIgnoreCase("exit")) {
                            jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Client");
                            jsonObject.put(Constants.receiver, machineName);
                            jsonObject.put(Constants.message, "");
                            jsonObject.put(Constants.type, Constants.disconnect);
                            output.writeUTF(jsonObject.toJSONString());
                            output.flush();
                            output.close();
//                            getClientToSendMessage();
                            break;
                        } else if (input.contains("init")) {
                            jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Client");
                            jsonObject.put(Constants.receiver, machineName);
                            jsonObject.put(Constants.message, input);
                            jsonObject.put(Constants.type, Constants.CLIENT_INIT);
                            output.writeUTF(jsonObject.toJSONString());
                            output.flush();
                            output.close();
                            socket = new Socket(Constants.localhostURL, portNumber);
                            output = new DataOutputStream(socket.getOutputStream());
                            bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                        } else {
                            jsonObject = new JSONObject();
                            jsonObject.put(Constants.sender, "Client");
                            jsonObject.put(Constants.receiver, machineName);
                            jsonObject.put(Constants.message, input);
                            jsonObject.put(Constants.type, Constants.message);
                            output.writeUTF(jsonObject.toJSONString());
                            output.flush();
                            output.close();
                            socket = new Socket(Constants.localhostURL, portNumber);
                            output = new DataOutputStream(socket.getOutputStream());
                            bufferedReader = new BufferedReader(new InputStreamReader(System.in));
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
                                            System.out.println(senderName + " Connected.");
                                            break;
                                        case Constants.disconnect:
                                            System.out.println(senderName + " Disconnected.");
                                            socket.close();
                                            break;
                                        case Constants.message:
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
        }
    }

//    private static void getClientToSendMessage() {
//        System.out.println("01. Enter 2 to send a message to Machine 2. \n02. Enter 3 to send a message to Machine 3.");
//        Scanner inputScanner = new Scanner(System.in);
//        String input = inputScanner.nextLine();
//        switch (input) {
//            case "2": {
//                connectToMachine(Constants.machine_2_listening_port, "Machine 2");
//                break;
//            }
//            case "3": {
//                connectToMachine(Constants.machine_3_listening_port, "Machine 3");
//                break;
//            }
//            default:
//                System.out.println("Invalid input! Please try again!");
//                getClientToSendMessage();
//                break;
//        }
//    }
}
