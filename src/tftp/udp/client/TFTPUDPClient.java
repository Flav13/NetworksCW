/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

/**
 *
 * @author fm263
 */
public class TFTPUDPClient {

    private DatagramSocket socket;
    private DatagramPacket packet;
    private InetAddress addr;

    private static Scanner in;
    // 'receiving' is used to determine wether the client should 
    // still be waiting for a packet when true or false when a request
    //is completed.
    private static boolean receiving = true;
    private ArrayList<Byte> file;
    private String req;
    private String fname;
    private int block;

    /**
     * When the client is initialised, several fields such as the server's IP
     * address and input scanner.
     *
     * @throws UnknownHostException
     * @throws SocketException
     */
    public TFTPUDPClient() throws UnknownHostException, SocketException {
        addr = InetAddress.getByName("127.0.0.1");

        in = new Scanner(System.in);
        file = new ArrayList<>();
        socket = new DatagramSocket(3000, addr);
        block = 1;
    }

    /**
     * The main method initialises a simple user interface that runs forever
     * unless the user wants to quit the client program.
     *
     * @param args the command line arguments
     * @throws java.net.UnknownHostException
     */
    public static void main(String[] args) throws UnknownHostException, IOException {
        TFTPUDPClient client = new TFTPUDPClient();

        while (true) {
            System.out.println("-------------");
            System.out.println("Select an option: ");
            System.out.println("1.Read/Write request (type req)");
            System.out.println("2.Quit (type quit)");
            System.out.println("-------------");
            String input;
            input = in.nextLine();
            if (input.equals("quit")) {
                System.exit(0);
            } else if (input.equals("req")) {
                client.sendRequest();
                //listen for packets until receiving is set to false
                while (receiving) {
                    client.receivePacket();
                }
                //reset receiving to true and await for another request
                receiving = true;
            } else {
                System.out.println("Invalid request");
            }

        }
    }

    /**
     * Used to handle the sending of a a read or write request to the server and
     * to deal with user input errors and set up the RRQ OR WRQ packets.
     *
     * @throws IOException
     */
    private void sendRequest() throws IOException {

        System.out.println("Enter file name:");
        fname = in.nextLine();
        System.out.println("Enter type of request:");
        req = in.nextLine();
        System.out.println(fname);
        byte[] buf;
        byte[] opcode = new byte[2];

        byte[] filename = fname.getBytes();
        while (!fnSize(filename)) {
            System.out.println("filename too large, please re-enter:");
            fname = in.nextLine();
            filename = fname.getBytes();
        }
        while (!validReq(req)) {
            System.out.println("Request not valid, please re-enter:");
            req = in.nextLine();
        }
        if (req.equals("read")) {
            opcode = "01".getBytes();
        } else if (req.equals("write")) {
            opcode = "02".getBytes();

        }
        buf = new byte[opcode.length + filename.length];
        int j = 0;
        for (int i = 0; i < buf.length; i++) {
            if (i < opcode.length) {
                buf[i] = opcode[i];
            } else {
                buf[i] = filename[j];
                j++;
            }
        }
        packet = new DatagramPacket(buf, buf.length);
        packet.setAddress(addr);
        packet.setPort(9000);

        sendPacket(packet);

    }

    /**
     * Used to handle the reception the packets. It checks the packet's opcode
     * to determine how the packet will be handled.
     *
     * @throws IOException
     */
    private void receivePacket() throws IOException {
        byte[] resp = new byte[516];
        packet = new DatagramPacket(resp, resp.length);
        socket.receive(packet);
        socket.setSoTimeout(0);
        resp = packet.getData();
        String s = new String(resp, "ASCII");

        char respOp = s.charAt(1);
        char blockNo = s.charAt(3);

        //if the packet received is an acknowledgement 
        if (respOp == '4') {
            if (req.equals("read")) {
                System.out.println("Request accepted !");
                System.out.println("Waiting for data...");
            } else {
                if (blockNo == '0') {
                    System.out.println("Request accepted !");
                    String input;
                    System.out.println("input data or write from a file? (type input or file)");
                    input = in.nextLine();
                    while (!validReq2(input)) {
                        System.out.println("Request not valid, please re-enter:");
                        input = in.nextLine();
                    }
                    if (input.equals("input")) {
                        System.out.println("Enter data to write to file: ");
                        input = in.nextLine();
                        sendData(input);

                    } else if (input.equals("file")) {
                        System.out.println("Input file name: ");
                        input = in.nextLine();
                        FileReader fr = new FileReader(input);
                        BufferedReader br = new BufferedReader(fr);
                        String toBeSent = "";
                        Object[] fileData = br.lines().toArray();
                        for (Object str : fileData) {
                            toBeSent = toBeSent + str;
                        }
                        sendData(toBeSent);

                    }
                } else {
                    System.out.println("Block no " + blockNo + " for the write data has ben acked.");
                    if (Integer.parseInt("" + blockNo) == block - 1) {
                        block = 1;
                    }
                    receiving = false;
                }
            }

            //if the packet is an error packet
        } else if (respOp == '5') {
            System.out.println("Request denied, file not found !");
            receiving = false;
            //is the packet is a data packet
        } else if (respOp == '3') {
            System.out.println("Data packet received...");

            byte[] blockNum = new byte[2];
            int j = 2;
            for (int i = 0; i < 2; i++) {

                blockNum[i] = resp[j];
                j++;

            }
            sendAck(blockNum);

            int dataSize = 0;
            for (int i = 4; i < resp.length; i++) {
                if (resp[i] != 0) {
                    dataSize++;

                }
            }
            byte[] data = new byte[dataSize];
            int fs = 4;
            for (int i = 0; i < dataSize; i++) {
                data[i] = resp[fs];
                fs++;

            }

            if (dataSize < 512) {
                receiving = false;

                for (int i = 0; i < dataSize; i++) {
                    file.add(data[i]);
                }
                printAndSaveFile(file);
                file = new ArrayList<>();

            } else {
                for (int i = 0; i < dataSize; i++) {
                    file.add(data[i]);

                }
            }

        }

    }

    /**
     * Used to handle the sending of data as strings
     *
     * @param input Data to be sent
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private void sendData(String input) throws UnsupportedEncodingException, IOException {
        ArrayList<DatagramPacket> toSend = new ArrayList<>();
        byte[] byteFileData = input.getBytes();
        int packetCounter = 0;
        if (byteFileData.length % 512 == 0) {

            for (int i = 0; i < byteFileData.length / 512; i++) {
                byte[] pack = new byte[512];
                int sP = packetCounter;
                packetCounter = packetCounter * 512;

                for (int t = 0; t < 512; t++) {
                    pack[t] = byteFileData[packetCounter];
                    packetCounter++;

                }
                packetCounter = sP;
                String s = new String(pack, "ASCII");
                packAfile(pack, toSend);
            }
        } else {
            //otherwise you'd need length/512 + 1 packets
            //to account for the last packet that is less than 512 bytes
            for (int i = 0; i < (byteFileData.length / 512) + 1; i++) {
                byte[] pack = new byte[512];
                //if you've reached the last set of bytes to go 
                //into a packet
                if (i == (byteFileData.length / 512)) {
                    //if the file is greater than 512 bytes
                    if (byteFileData.length > 512) {

                        //calculate how many bytes in the last bit of file
                        int counter = 0;
                        int c1 = byteFileData.length - packetCounter * 512;
                        int c2 = packetCounter * 512;
                        for (int ex = 0; ex < c1; ex++) {
                            if (byteFileData[c2] != 0) {
                                counter++;
                                c2++;
                            }
                        }
                        pack = new byte[counter];

                        //add those bytes to the pack array
                        packetCounter = packetCounter * 512;
                        for (int t = 0; t < pack.length; t++) {
                            pack[t] = byteFileData[packetCounter];
                            packetCounter++;
                        }
                    } else {
                        for (int t = 0; t < byteFileData.length; t++) {
                            pack[t] = byteFileData[t];

                        }
                    }
                    //  String s = new String(pack, "ASCII");
                    packAfile(pack, toSend);

                } else {
                    int sP = packetCounter;
                    // System.out.println(packetCounter);

                    packetCounter = packetCounter * 512;

                    for (int t = 0; t < 512; t++) {
                        pack[t] = byteFileData[packetCounter];
                        packetCounter++;

                    }
                    packetCounter = sP;
                    packetCounter++;
                    //   String s = new String(pack, "ASCII");
                    packAfile(pack, toSend);
                }
            }
        }

        for (DatagramPacket p : toSend) {

            p.setAddress(addr);
            p.setPort(9000);
            sendPacket(p);

        }
    }

    /**
     * Used to add an array of data bytes to a packet ready to be sent.
     *
     * @param data Data to be sent
     * @param toSend ArrayList that stores data packets formed from the string
     */
    private void packAfile(byte[] data, ArrayList<DatagramPacket> toSend) {
        byte[] packetInBytes = new byte[data.length + 4];
        byte[] opcode = "03".getBytes();
        byte[] blockNum = ("" + 0 + "" + block).getBytes();
        int l = 0;
        for (int i = 0; i < opcode.length + blockNum.length; i++) {
            if (i < 2) {
                packetInBytes[i] = opcode[i];
            } else {

                packetInBytes[i] = blockNum[l];
                l++;
            }

        }

        int g = 4;
        for (int i = 0; i < data.length; i++) {
            packetInBytes[g] = data[i];
            g++;

        }
        packet = new DatagramPacket(packetInBytes, packetInBytes.length);
        toSend.add(packet);
        block++;

    }

    /**
     * Used to handle the sending of acknowledgments
     *
     * @param blockNum
     * @throws IOException
     */
    private void sendAck(byte[] blockNum) throws IOException {
        String blNum = new String(blockNum);
        System.out.println("Sending ack for block num " + blNum);
        byte[] opcode = "04".getBytes();
        int len = opcode.length + blockNum.length;
        byte[] ackData = new byte[len];
        int j = 0;
        for (int i = 0; i < ackData.length; i++) {
            if (i < 2) {
                ackData[i] = opcode[i];
            } else {
                ackData[i] = blockNum[j];
                j++;
            }
        }
        DatagramPacket ackPack = new DatagramPacket(ackData, ackData.length);
        ackPack.setAddress(addr);
        ackPack.setPort(9000);

        sendPacket(ackPack);

    }

    /**
     * checks that the file name(in an array of bytes) isn't too big
     *
     * @param fn The name of the file in an array of bytes
     * @return
     */
    private static boolean fnSize(byte[] fn) {
        return fn.length < 31;
    }

    /**
     * Checks if the request entered by the user is valid
     *
     * @param req The user input
     * @return
     */
    private static boolean validReq(String req) {
        return req.equals("read") || req.equals("write");
    }

    /**
     * Checks if the input is a valid method of writing to a file
     *
     * @param req The user input
     * @return
     */
    private static boolean validReq2(String req) {
        return req.equals("input") || req.equals("file");
    }

    /**
     * Used to deal with displaying the contents of the data received from the
     * server.
     *
     * @param file ArrayList of Bytes that contain the file data
     * @throws UnsupportedEncodingException
     */
    private void printAndSaveFile(ArrayList<Byte> file) throws UnsupportedEncodingException, IOException {
        Byte[] fileData = file.toArray(new Byte[file.size()]);
        String str = new String(toPrimitives(fileData), "ASCII");
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write(str);
        }
        System.out.println("File data: ");
        System.out.println(str);
    }

    /**
     * 
     * Used to convert an array of type Byte to the primitive byte
     *
     * @param fileData Data in a Byte array to be converted
     * @return
     */
   private byte[] toPrimitives(Byte[] fileData) {
        byte[] pFileData = new byte[fileData.length];

        for (int i = 0; i < fileData.length; i++) {
            pFileData[i] = fileData[i];
        }

        return pFileData;
    }

    /**
     * Used to send a packet and initialise a timer. If the timer is fired The
     * method is called again.
     *
     * @param packet The packet to be sent
     * @throws IOException
     */
    private void sendPacket(DatagramPacket packet) throws IOException {
        try {
            socket.send(packet);
            socket.setSoTimeout(1000);

        } catch (SocketTimeoutException ex) {
            sendPacket(packet);
        }
    }

}
