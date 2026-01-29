/**
 * @author Grupo 51
 * @author Henrique Catarino - 56278
 * @author Miguel Nunes - 56338
 * @author Vasco Maria - 56374
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class TintolmarketServer {
	
	private static File LOGINS_FILE;
	private static File WALLETS_FILE;
	private static File WINES_FILE;
	private static File MSG_FILE;
	
	private static HashMap<String, String> LOGINS;
	private static HashMap<String, Integer> WALLETS;
	private static HashMap<String, Wine> WINES;
	private static HashMap<String, ArrayList<String>> MESSAGES;
	
	public static void main(String[] args) throws IOException {
		int port = 12345;
		if (args.length != 0 ) {
			port = Integer.parseInt(args[0]);
		}
		LOGINS_FILE = new File("logins.txt");
		LOGINS_FILE.createNewFile();
		LOGINS = new HashMap<>();
		BufferedReader buffReader = new BufferedReader(new FileReader(LOGINS_FILE));
		String line;
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit=line.split(":");
			LOGINS.put(lineSplit[0], lineSplit[1]);
		}
		buffReader.close();
		WALLETS_FILE = new File("wallets.txt");
		WALLETS_FILE.createNewFile();
		WALLETS = new HashMap<>();
		buffReader = new BufferedReader(new FileReader(WALLETS_FILE));
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(":");
			WALLETS.put(lineSplit[0], Integer.parseInt(lineSplit[1]));
		}
		buffReader.close();
		WINES_FILE = new File("wines.txt");
		WINES_FILE.createNewFile();
		new File("./images").mkdir();
		WINES = new HashMap<>();
		buffReader = new BufferedReader(new FileReader(WINES_FILE));
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(";");
			String[] sectionSplit = lineSplit[0].split(":");
			Wine wine = new Wine(sectionSplit[0], new File(sectionSplit[1]), Double.parseDouble(sectionSplit[2]),
					Integer.parseInt(sectionSplit[3]));
			for (int i = 1; i < lineSplit.length; i++) {
				sectionSplit = lineSplit[i].split(":");
				wine.sellWine(sectionSplit[0], Integer.parseInt(sectionSplit[1]), Integer.parseInt(sectionSplit[2]));
			}
			WINES.put(wine.getName(), wine);
		}
		buffReader.close();
		MSG_FILE = new File("messages.txt");
		MSG_FILE.createNewFile();
		MESSAGES = new HashMap<>();
		buffReader = new BufferedReader(new FileReader(MSG_FILE));
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(";");
			ArrayList<String> messages = new ArrayList<>();
			for (int i = 1; i < lineSplit.length; i++) {
				messages.add(lineSplit[i]);
			}
			MESSAGES.put(lineSplit[0], messages);
		}
		buffReader.close();
		TintolmarketServer server = new TintolmarketServer();
		server.startServer(port);
	}

	@SuppressWarnings("resource")
	public void startServer(int port) {
		ServerSocket sSoc = null;
		try {
			sSoc = new ServerSocket(port);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}	
		while (true) {
			try {
				Socket inSoc = sSoc.accept();
				ServerThread newServerThread = new ServerThread(inSoc);
				newServerThread.start();
		    }
		    catch (IOException e) {
		        e.printStackTrace();
		    }
		}
	}
	
	private class ServerThread extends Thread {

		private Socket socket;

		ServerThread(Socket inSoc) {
			socket = inSoc;
		}
 
		public void run() {
			boolean stop = false;
			try {
				ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
				String user = (String) inStream.readObject();
				String password = (String) inStream.readObject();
				if (!authenticate(user, password)) {
					stop = true;
					outStream.writeBoolean(false);
				}
				else {
					outStream.writeBoolean(true);
				}
				outStream.flush();
				while (!stop) {
					String command = (String) inStream.readObject();
					if (command.equals("add") || command.equals("a")) {
						String name = (String) inStream.readObject();
						if (WINES.containsKey(name)) {
							outStream.writeBoolean(false);
						}
						else {
							outStream.writeBoolean(true);
							outStream.flush();
							long size = inStream.readLong();
							FileOutputStream fileOut = new FileOutputStream("./images/" + name + ".jpg");
							byte[] buffer = new byte [1024];
							int bytes = 0;
							while(size > 0 && (bytes = inStream.read(buffer, 0,
									(int) Math.min(buffer.length, size))) != -1) {
								fileOut.write(buffer, 0, bytes);
								size -= bytes;
							}
							fileOut.close();
							boolean success = add(name, new File("./images/" + name + ".jpg"));
							outStream.writeBoolean(success);
						}
					}
					else if (command.equals("sell") || command.equals("s")) {
						boolean success = sell(user, (String) inStream.readObject(),
								inStream.readInt(), inStream.readInt());
						outStream.writeBoolean(success);
					}
					else if (command.equals("view") || command.equals("v")) {
						String name = (String) inStream.readObject();
						//position 0 is String
						//position 1 is image File
						ArrayList<Object> response = viewWine(name);
						if (response == null) {
							outStream.writeBoolean(false);
						}
						else {
							outStream.writeBoolean(true);
							outStream.writeObject(response.get(0));
							FileInputStream fileIn = new FileInputStream((File) response.get(1));
							outStream.writeLong(((File) response.get(1)).length());
							byte[] buffer = new byte[1024];
							int read = 0;
							while((read = fileIn.read(buffer)) != -1) {
								outStream.write(buffer, 0, read);
								outStream.flush();
							}
							fileIn.close();
						}
					}
					else if (command.equals("buy") || command.equals("b")) {
						int success = buy(user, (String) inStream.readObject(), (String) inStream.readObject(),
								inStream.readInt());
						outStream.writeInt(success);
					}
					else if (command.equals("wallet") || command.equals("w")) {
						outStream.writeInt(viewBalance(user));
					}
					else if (command.equals("classify") || command.equals("c")) {
						boolean success = classify((String) inStream.readObject(), inStream.readInt());
						outStream.writeBoolean(success);
					}
					else if (command.equals("talk") || command.equals("t")) {
						boolean success = saveMessage(user, (String) inStream.readObject(),
								(String) inStream.readObject());
						outStream.writeBoolean(success);
					}
					else if (command.equals("read") || command.equals("r")) {
						String messages = getMessages(user);
						outStream.writeObject(messages);
					}
					outStream.flush();
				}
				outStream.close();
				inStream.close();
				socket.close();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
 
	}
	
	private synchronized boolean authenticate(String user, String password) throws IOException {
		if (LOGINS.get(user) != null) {
			return LOGINS.get(user).equals(password);
		}
		LOGINS.put(user, password);
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(LOGINS_FILE, true));
		buffWriter.write(user + ':' + password + "\n");
		buffWriter.close();
		WALLETS.put(user, 200);
		buffWriter = new BufferedWriter(new FileWriter(WALLETS_FILE, true));
		buffWriter.write(user + ":200\n");
		buffWriter.close();
		return true;
	}
	
	private synchronized boolean add(String wineName, File imagem) throws IOException {
		if (WINES.containsKey(wineName)) {
			return false ;
		}
		else {
			WINES.put(wineName, new Wine(wineName, imagem));
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WINES_FILE, true));
			buffWriter.write(wineName + ":" + imagem.getPath() + ":0:0\n");
			buffWriter.close();
			return true;
		}
	}
	
	private synchronized boolean sell(String user, String wineName, int quantity, int value) throws IOException {
		if (WINES.containsKey(wineName)) {
			BufferedReader buffReader = new BufferedReader(new FileReader(WINES_FILE));
			StringBuilder lines = new StringBuilder();
			String line;
			while ((line = buffReader.readLine()) != null) {
				if (line.split(":")[0].equals(wineName)) {
					WINES.get(wineName).sellWine(user, quantity, value);
					lines.append(line + ";" + user + ":" + quantity + ":" + value + "\n");
				}
				else {
					lines.append(line + "\n");
				}
			}
			buffReader.close();
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WINES_FILE));
			buffWriter.write(lines.toString());
			buffWriter.close();
			return true;
		} else {
			return false;
		}
	}

	private synchronized int buy(String user, String wine, String seller, int quantity) throws IOException {
		if (WINES.containsKey(wine)) {
			if (WINES.get(wine).getSale(seller).get(0) >= quantity) {
				if (WINES.get(wine).getSale(seller).get(1) * quantity <= WALLETS.get(user) ) {
					int value = WINES.get(wine).getSale(seller).get(1);
					changeBalance(seller, quantity, value);
					changeBalance(user, -quantity, value);
					WINES.get(wine).buyWine(seller, quantity);
					changeQuantity(seller, quantity);
					return 0; //all good
				}
				else {
					return -3; //broke
				}
			}
			else {
				return -2; //not enough wine
			}
		}
		else {
			return -1; //wine does not exist
		}
	}
	
	private synchronized void changeBalance(String username, int quantity, int value) throws IOException {
		BufferedReader buffReader = new BufferedReader(new FileReader(WALLETS_FILE));
		StringBuilder lines = new StringBuilder();
		String line;
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(":");
			if (lineSplit[0].equals(username)) {
				WALLETS.put(username, (WALLETS.get(username)) + quantity * value);
				line = username + ":" + ((Integer.parseInt(lineSplit[1]) + quantity * value));
			}
			lines.append(line + "\n");
		}
		buffReader.close();
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WALLETS_FILE));
		buffWriter.write(lines.toString());
		buffWriter.close();
	}
	
	private synchronized Integer viewBalance(String user) {
		return WALLETS.get(user);
	}
	
	//position 0 is String
	//position 1 is image File
	private synchronized ArrayList<Object> viewWine(String wineName) {
		if (!WINES.containsKey(wineName)) {
			return null;
		}
		Wine wine = WINES.get(wineName);
		StringBuilder sb = new StringBuilder();
		sb.append("Wine: " + wineName + "\n");
		sb.append("Rating: " + wine.getRating() + "\n");
		HashMap<String, ArrayList<Integer>> sales = wine.getSales();
		if(sales.size() > 0) {
			sb.append("Sales: \n");
			for(Entry<String, ArrayList<Integer>> sale : sales.entrySet()) {
				sb.append("\t" + sale.getKey() + " has a stock of " + sale.getValue().get(0) +
						" for $" + sale.getValue().get(1) + " each.\n");
			}
		}
		ArrayList<Object> response = new ArrayList<>();
		response.add(sb.toString());
		response.add(wine.getImage());
		return response;
	}
	
	private synchronized boolean classify(String wineName, int stars) throws IOException {
		Wine wine = WINES.get(wineName);
		if (wine != null) {
			BufferedReader buffReader = new BufferedReader(new FileReader(WINES_FILE));
			StringBuilder lines = new StringBuilder();
			String line;
			while ((line = buffReader.readLine()) != null) {
				String[] lineSplit = line.split(";"); //lineSplit[0] = porto:porto.png:4.5:17
				String[] sectionSplit = lineSplit[0].split(":");
				if (sectionSplit[0].equals(wineName)) {
					lines.append(sectionSplit[0] + ":" + sectionSplit[1] + ":" + wine.rate(stars) + ":" +
						wine.getNumberRaters());
					for (int i = 1; i < lineSplit.length; i++) {
						lines.append(";" + lineSplit[i]);
					}
					lines.append("\n");
				}
				else {
					lines.append(line + "\n");
				}
			}
			buffReader.close();
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WINES_FILE));
			buffWriter.write(lines.toString());
			buffWriter.close();
			return true;
		}
		return false;
	}
	
	private synchronized boolean saveMessage(String user, String username, String message) throws IOException {
		if (LOGINS.get(username) == null) {
			return false;
		}
		ArrayList<String> messages = MESSAGES.get(username);
		if (messages == null) {
			messages = new ArrayList<>();
		}
		messages.add(user + ":" + message);
		MESSAGES.put(username, messages);
		StringBuilder lines = new StringBuilder();
		for (Entry<String, ArrayList<String>> entry : MESSAGES.entrySet()) {
			messages = entry.getValue();
			lines.append(entry.getKey());
			for (int i = 0; i < messages.size(); i++) {
				lines.append(";" + messages.get(i));
			}
			lines.append("\n");
		}
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(MSG_FILE));
		buffWriter.write(lines.toString());
		buffWriter.close();
		return true;
	}
	
	private synchronized String getMessages(String user) throws IOException {
		StringBuilder messages = new StringBuilder();
		BufferedReader buffReader = new BufferedReader(new FileReader(MSG_FILE));
		StringBuilder lines = new StringBuilder();
		String line;
		while ((line = buffReader.readLine()) != null) {
			if (line.split(";")[0].equals(user)) {
				ArrayList<String> messageList = MESSAGES.get(user);
				for (String msg : messageList) {
					String[] msgSplit = msg.split(":");
					messages.append("From: " + msgSplit[0] + "\n" + msgSplit[1] + "\n");
				}
			}
			else {
				lines.append(line + "\n");
			}
		}
		buffReader.close();
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(MSG_FILE));
		buffWriter.write(lines.toString());
		buffWriter.close();
		if (messages.toString().equals("")) {
			return "No Messages!\n";
		}
		else {
			return messages.toString();
		}
	}
	
	private synchronized void changeQuantity(String username, int quantity) throws IOException {
		BufferedReader buffReader = new BufferedReader(new FileReader(WINES_FILE));
		StringBuilder lines = new StringBuilder();
		String line;
		while ((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(";"); //lineSplit[0] = porto:porto.png:4.5:17
			lines.append(lineSplit[0]);
			for (int i = 1; i < lineSplit.length; i++) {
				String[] lineSplit2 = lineSplit[i].split(":"); //lineSplit2 = [vasco][10][200]
				if (lineSplit2[0].equals(username)) {
					if (Integer.parseInt(lineSplit2[1]) > quantity) {
						int p = Integer.parseInt(lineSplit2[1]) - quantity;
						lines.append(";" + lineSplit2[0] + ":" + p + ":" + lineSplit2[2]);
					}
				}
				else {
					lines.append(";" + lineSplit2[0]+ ":" + lineSplit2[1]+ ":" + lineSplit2[2]);
				}
			}
		}
		buffReader.close();
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WINES_FILE));
		buffWriter.write(lines.toString());
		buffWriter.close();
	}
}