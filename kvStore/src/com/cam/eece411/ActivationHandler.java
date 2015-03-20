package com.cam.eece411;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.cam.eece411.Messages.MessageBuilder;
import com.cam.eece411.Messages.ReceivedMessage;
import com.cam.eece411.Utilities.Helper;
import com.cam.eece411.Utilities.Protocols;

public class ActivationHandler implements Runnable {
	private ReceivedMessage rcvdMsg;

	public ActivationHandler(DatagramPacket packet) {
		rcvdMsg = new ReceivedMessage(packet);
	}

	public void run() {
		/*
		 * If it is a CREATE_DHT command and we aren't in the table (and we are also
		 * in an UNACTIVATED state) then start the DHT with yourself
		 */
		if (rcvdMsg.getCommand() == Protocols.CMD_CREATE_DHT && !Circle.containsNode(Server.me)) {
			respondToCREATE();
		}
		else if (rcvdMsg.getCommand() == Protocols.CMD_START_JOIN_REQUESTS && !Circle.containsNode(Server.me)) {
			respondToSTART_JOIN_REQUESTS();
		}
	}

	private void respondToCREATE() {
		synchronized(Circle.class) {
			// Add ourself as the first node in the circle
			Circle.add(Server.me);
			Server.state = Protocols.IN_TABLE;
		}
		// Read all the nodes to be in the system from the file
		FileInputStream fin;
		String[] nodeList = new String[Helper.NUM_NODES];
		try {
			// Open an input stream
			fin = new FileInputStream("testingNodes.txt");
			// Read a line of text
			DataInputStream din = new DataInputStream(fin);
			for (int i = 0; i < Helper.NUM_NODES; i++) {
				nodeList[i] = din.readLine();
			}
			// Close the input stream
			fin.close();
		} catch (IOException e) {
			System.out.println("Unable to read from file");
			e.printStackTrace();
			System.exit(-1);
		}
		// Get a list of all the IPs, excluding our own
		ArrayList<InetAddress> ips = new ArrayList<InetAddress>();
		try {
			for (int i = 0; i < Helper.NUM_NODES; i++) {
				if (Server.me.name.equalsIgnoreCase(nodeList[i].trim())) {
					ips.add(InetAddress.getByName(nodeList[i].trim()));
				}
			}
		} catch (UnknownHostException e) {
			System.err.println("Well shit, ain't no host");;
			e.printStackTrace();
		}
		// Generate the 'start requesting to join' message
		byte[] startYourEngines = new byte[17];
		for (int i = 0; i < 17; i++) {
			startYourEngines[i] = 0;
		}
		startYourEngines[16] = Protocols.CMD_START_JOIN_REQUESTS;
		// Message them all to start requesting randomly to join ("activate them")
		for (int i = 0; i < ips.size(); i++) {
			Server.sendMessage(startYourEngines, ips.get(i), Protocols.LISTENING_PORT);
		}
	}

	private void respondToSTART_JOIN_REQUESTS() {
		// Read all the nodes to start querying
		FileInputStream fin;
		String[] nodeList = new String[Helper.NUM_NODES];
		try {
			// Open an input stream
			fin = new FileInputStream("testingNodes.txt");
			// Read a line of text
			DataInputStream din = new DataInputStream(fin);
			for (int i = 0; i < Helper.NUM_NODES; i++) {
				nodeList[i] = din.readLine();
			}
			// Close the input stream
			fin.close();
		} catch (IOException e) {
			System.out.println("Unable to read from file");
			e.printStackTrace();
			System.exit(-1);
		}
		// Message a random node that isn't us with a request to join
		synchronized(Circle.class) {
			int random;
			while (!Circle.containsNode(Server.me)) {
				// Get a random number from 0 - (NUM_NODES - 1)
				random = (int)(Math.random()*(nodeList.length - 1));
				// Message the random node if it isn't us
				if (!Server.me.name.equalsIgnoreCase(nodeList[random].trim())) {
					try {
						Server.sendMessage(MessageBuilder.requestToJoin(), InetAddress.getByName(nodeList[random].trim()), Protocols.LISTENING_PORT);
						// Wait for a reply
						awaitJoinResponse();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}
		}
	}

	private void awaitJoinResponse() {
		try {
			byte[] receiveData = new byte[Protocols.MAX_MSG_SIZE];
			DatagramSocket socket = new DatagramSocket(Protocols.JOIN_RESPONSE_PORT);
			DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
			socket.setSoTimeout(Protocols.JOIN_TIMEOUT);
			socket.receive(packet);
			socket.close();
			respondToJOIN_RESPONSE();
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	}

	private void respondToJOIN_RESPONSE() {
		// From received message, determine your:
		/*
		 * From received message, get your:
		 * 	- node number
		 * 	- next node number
		 * 	- view of the system
		 * 	- notice of how many files are about to be coming your way
		 * Then wait for and receive all these files/key-value pairs
		 * Then notify the node that sent you them, so he can add you to his circle,
		 * and notify all his circle's nodes
		 */
	}
}