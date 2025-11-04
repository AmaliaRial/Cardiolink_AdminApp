import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Admin {
    public static void main(String[] args) {
        String serverAddress = null;
        int port = 0;
        Socket socket = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        Scanner scanner = new Scanner(System.in);
        String option;
        while (true) {
            System.out.println("Enter the IP address: ");
            serverAddress = scanner.nextLine();
            if (isValidIPAddress(serverAddress)) {
                break;
            } else {
                System.out.println("Invalid IP address format. Please try again.");
            }
        }
        while (true) {
            System.out.println("Enter the port number: ");
            try {
                port = Integer.parseInt(scanner.nextLine());
                if (port >= 1024 && port <= 65535) {
                    break;
                } else {
                    System.out.println("Port number must be between 1024 and 65535. Please try again.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Please enter a numeric value.");
            }
        }
        try{
            socket = new Socket(serverAddress.toLowerCase(), port);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream.writeUTF("ADMMIN");
            while(true){
                System.out.println("Select an option: ");
                System.out.println("Shut down. Close the server");
                System.out.println("Exit. Exit");
                option = scanner.nextLine();
                while(!option.equalsIgnoreCase("Shut down") &&
                        !option.equalsIgnoreCase("Exit")){
                    option = scanner.nextLine();
                }
                outputStream.writeUTF(option);
                switch (option.toLowerCase()){
                    case "shut down":
                        System.out.println(inputStream.readUTF());
                        releaseresources(socket,outputStream,scanner,inputStream);
                        System.exit(0);
                    case "exit":
                        releaseresources(socket,outputStream,scanner,inputStream);
                        System.exit(0);
                }
            }
        }catch(Throwable e){
            Logger.getLogger(Admin.class.getName()).log(Level.SEVERE, "Error in the client", e);
        }
    }

    private static void releaseresources(Socket socket,DataOutputStream outputStream, Scanner scanner,DataInputStream inputStream){
        if (scanner != null) {
            scanner.close();
        }
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            Logger.getLogger(Admin.class.getName()).log(Level.SEVERE, "Error closing OutputStream", e);
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            Logger.getLogger(Admin.class.getName()).log(Level.SEVERE, "Error closing InputStream", e);
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Logger.getLogger(Admin.class.getName()).log(Level.SEVERE, "Error closing socket", e);
        }
    }

    private static boolean isValidIPAddress(String ip) {
        if(ip.equalsIgnoreCase("localhost")){
            return true;
        } else {
            // Divide the ip by the .
            String[] octets = ip.split("\\.");
            // There need to be 4 octets
            if (octets.length != 4) {
                return false;
            }
            // check the octect
            for (String octet : octets) {
                try {
                    int value = Integer.parseInt(octet);
                    // Check if it is between the correct numbers
                    if (value < 0 || value > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // if can´t be parsed is incorrect
                    return false;
                }
            }
            // if everything seems fine the ip is correct
            return true;
        }
    }

}
