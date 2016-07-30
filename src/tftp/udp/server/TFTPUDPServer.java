/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tftp.udp.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fm263
 */
public class TFTPUDPServer extends Thread {

    protected DatagramSocket socket;
    private byte[] data;
    private byte[] fNameBytes;
    private DatagramPacket packet;
    ArrayList<Byte> file;
    int block;
    char ackedBlock;
    String fName;
    int clientPort;
    InetAddress clientInetAddress;

    /**
     *
     * @throws SocketException
     * @throws UnknownHostException
     */
    public TFTPUDPServer() throws SocketException, UnknownHostException {
        this("TFTPUDPServer");
        block = 1;
        socket = new DatagramSocket(9000);
        ackedBlock = 1;
        file = new ArrayList<>();
        clientPort=0;

    }

    public TFTPUDPServer(String name) throws SocketException {
        super(name);

    }

    /**
     * Used to receive packets and reset the timer each time a packet is
     * received.
     */
    @Override
    public void run() {
        byte[] recvBuf = new byte[516];
        while (true) {
            packet = new DatagramPacket(recvBuf, recvBuf.length);
            try {
                socket.receive(packet);
                clientPort=packet.getPort();
                clientInetAddress = packet.getAddress();
                
                socket.setSoTimeout(0);
                System.out.println("Packet received");
            } catch (IOException ex) {
                Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
            }

            data = packet.getData();
            recvBuf = new byte[516];
            packet.setData(recvBuf);

            fNameBytes = new byte[516];
            receivePacket();

        }

    }

    /**
     * Handles the receiving of data by checking the opcode and dealing with the
     * packet accordingly.
     */
    private void receivePacket() {
        String s = new String(data);
        // if the packet is a RRQ packet
        if (s.charAt(1) == '1') {
            try {

                sendData();
            } catch (IOException ex) {
            }
            //if the packet is an ACK packet 
        } else if (s.charAt(1) == '4') {
            System.out.println("Ack for block num " + s.charAt(3) + " received");
            if (Integer.parseInt("" + s.charAt(3)) == block - 1) {
                block = 1;
            }
            // if the packet is a WRQ packet
        } else if (s.charAt(1) == '2') {
            int dataSize = 0;
            for (int i = 2; i < data.length; i++) {
                if (data[i] != 0) {

                    dataSize++;
                }
            }
            byte[] filename = new byte[dataSize];

            int j = 2;
            for (int i = 0; i < dataSize; i++) {
                filename[i] = data[j];
                j++;

            }
            fName = new String(filename);

            try {
                FileReader fr = new FileReader(fName);
                sendAck("00");
            } catch (FileNotFoundException ex) {
                sendErrPacket();
            } catch (IOException ex) {}
            //if the packet is a data packet
        } else if (s.charAt(1) == '3') {
            System.out.println("Data received...");
            
            byte[] blockNum = new byte[2];
            int j = 2;
            
            for (int i = 0; i < 2; i++) {
                blockNum[i] = data[j];
                j++;
            }
            try {
                sendAck(new String(blockNum));
            } catch (IOException ex) {}
            int dataSize = 0;
            for (int i = 4; i < data.length; i++) {
                if (data[i] != 0) {
                    dataSize++;

                }
            }
            byte[] dataInBytes = new byte[dataSize];
            int fs = 4;
            for (int i = 0; i < dataSize; i++) {
                dataInBytes[i] = data[fs];
                fs++;

            }
            if (dataSize < 512) {
                for (int i = 0; i < dataSize; i++) {
                    file.add(dataInBytes[i]);

                }
                try {

                    FileWriter fw = new FileWriter(fName, true);

                    try (BufferedWriter bw = new BufferedWriter(fw)) {
                        Byte[] fileData = file.toArray(new Byte[file.size()]);

                        String str = new String(toPrimitives(fileData), "ASCII");
                        bw.write(" " + str);

                    }
                    System.out.println("Successfully written to file!");
                    file = new ArrayList<>();
                } catch (IOException ex) {
                    sendErrPacket();
                }

            } else {
                for (int i = 0; i < dataSize; i++) {
                    file.add(data[i]);

                }
            }
        }
    }

    /**
     * Used to handle the sending of acknowledgments by taking the block number.
     * parameter as a string.
     *
     * @param s The block number
     * @throws IOException
     */
    private void sendAck(String s) throws IOException {
        if (s.equals("00")) {
            System.out.println("Request to read/write received");
        }

        byte[] ackPack = new byte[4];
        byte[] opcode1 = "04".getBytes();
        byte[] blockNum1 = s.getBytes();
        int w = 0;
        for (int i = 0; i < 4; i++) {
            if (i < opcode1.length) {
                ackPack[i] = opcode1[i];
            } else {
                ackPack[i] = blockNum1[w];
                w++;
            }
        }

        DatagramPacket ackPacket = new DatagramPacket(ackPack, ackPack.length);
        ackPacket.setPort(clientPort);
        ackPacket.setAddress(clientInetAddress);
        sendPacket(ackPacket);

    }

    /**
     * Used to deal with the sending of data packets. Packets are stored in an
     * array list as they are formed then sent.
     *
     * @throws IOException
     */
    private void sendData() throws IOException {

        //get the file name
        int j = 0;
        for (int i = 2; i < data.length; i++) {
            fNameBytes[j] = data[i];
            j++;
        }
        String filename = null;
        try {
            filename = new String(fNameBytes, "ASCII");
            filename = filename.trim();
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(TFTPUDPServer.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            FileReader fr = new FileReader(filename);
            sendAck("00");
            System.out.println("Sending data...");
            BufferedReader br = new BufferedReader(fr);

            Object[] fileData = br.lines().toArray();

            String[] stringArray = Arrays.copyOf(fileData, fileData.length, String[].class);
            ArrayList<DatagramPacket> toSend = new ArrayList<>();
            String fileDataInString = "";
            for (String str : stringArray) {
                fileDataInString += str + " ";

            }
            
            byte[] byteFileData = fileDataInString.getBytes();
            int packetCounter = 0; // used to know the current position in the file

            //if the number of bytes is divisible by 512
            //they you'll need length/512 packets
            if (byteFileData.length % 512 == 0) {
                
                for (int i = 0; i < byteFileData.length / 512; i++) {
                    byte[] pack = new byte[512];
                    int sP = packetCounter;
                    packetCounter = packetCounter * 512;
                    
                    for (int t = 0; t < 512; t++) {
                        pack[t] = byteFileData[packetCounter];
                        packetCounter++;

                    }
                    packetCounter=sP;
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
                        packAfile(pack, toSend);

                    } else {
                        int sP = packetCounter;
                        packetCounter = packetCounter * 512;

                        for (int t = 0; t < 512; t++) {
                            pack[t] = byteFileData[packetCounter];
                            packetCounter++;

                        }
                        packetCounter = sP;
                        packetCounter++;
                        packAfile(pack, toSend);
                    }
                }
            }

            for (DatagramPacket p : toSend) {
                p.setAddress(clientInetAddress);
                p.setPort(clientPort);
                sendPacket(p);

            }

        } catch (FileNotFoundException ex) {
            sendErrPacket();
        }

    }

    /**
     *Used to deal with sending an error packet if the file requested by the client
     * does not exist.
     */
    private void sendErrPacket() {
        System.out.println("File not found");
        byte[] opcode = "05".getBytes();
        byte[] errcode = "01".getBytes();
        byte[] errmsg = "File not found!".getBytes();
        int packLen = opcode.length + errcode.length + errmsg.length;
        byte[] errorPack = new byte[packLen];

        int i2 = 0;
        int i3 = 0;
        for (int i = 0; i < errorPack.length; i++) {
            if (i < 2) {
                errorPack[i] = opcode[i];
            } else if (i < 4) {
                errorPack[i] = errcode[i2];
                i2++;
            } else {
                errorPack[i] = errmsg[i3];
                i3++;

            }
        }
        DatagramPacket errPacket = new DatagramPacket(errorPack, errorPack.length);
        errPacket.setPort(clientPort);
        errPacket.setAddress(clientInetAddress);
        try {
            socket.send(errPacket);
        } catch (IOException exc) {
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
     * Used to convert an array of type Byte to the primitive byte
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
     * Used to send a packet and initialise a timer. If the timer is fired
     * The method is called again.
     * @param packet The packet to be sent 
     * @throws IOException 
     */
    private void sendPacket(DatagramPacket packet) throws IOException {
        try {
            socket.send(packet);
            socket.setSoTimeout(10000);

        } catch (SocketTimeoutException ex) {
            sendPacket(packet);

        }
    }

    public static void main(String[] args) throws SocketException, UnknownHostException {
        new TFTPUDPServer().start();

        System.out.println("TFTP Server Started");
    }
}
