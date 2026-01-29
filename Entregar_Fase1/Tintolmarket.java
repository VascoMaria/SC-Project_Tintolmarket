/**
 * @author Grupo 51
 * @author Henrique Catarino - 56278
 * @author Miguel Nunes - 56338
 * @author Vasco Maria - 56374
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Tintolmarket {
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		if (args.length < 2 || args.length > 3) {
			System.out.println("Wrong format used. Please use \"Tintolmarket "
					+ "<IP/hostname>[:port] <userID> [password]\"");
			System.exit(-1);
		}
		String[] address = args[0].split(":");
		String host = address[0];
		int port = 12345;
		if (address.length > 1) {
			try {
				port = Integer.parseInt(address[1]);
			} catch (NumberFormatException e) {
				System.out.println("Wrong format used. Please use \"Tintolmarket "
						+ "<IP/hostname>[:port] <userID> [password]\"");
				System.exit(-1);
			}
		}
		String user = args[1];
		String password;
		Scanner input = new Scanner(System.in);
		if (args.length == 3) {
			password = args[2];
		}
		else {
			System.out.print("Password: ");
			password = input.nextLine();
		}
		Socket socket = new Socket(host, port);
		ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
		ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
		outStream.writeObject(user);
		outStream.writeObject(password);
		outStream.flush();
		boolean authenticated = inStream.readBoolean();
		if (!authenticated) {
			System.out.println("Authentication error: Password doesn't match existing one!");
			input.close();
			inStream.close();
			outStream.close();
			socket.close();
			System.exit(-1);
		}
		System.out.println("Authentication successful.");
		System.out.println("Hello " + user + "!");
		System.out.println();
		System.out.println("Menu:");
		System.out.println("add <wine> <image>");
		System.out.println("sell <wine> <value> <quantity>");
		System.out.println("view <wine>");
		System.out.println("buy <wine> <seller> <quantity>");
		System.out.println("wallet");
		System.out.println("classify <wine> <stars>");
		System.out.println("talk <user> <message>");
		System.out.println("read");
		System.out.println("quit");
		System.out.println();
		boolean stop = false;
		Loop:
		while (!stop) {
			System.out.print("Command: ");
			String[] command = input.nextLine().split(" ");
			if ((command[0].equals("add") || command[0].equals("a")) && command.length == 3) {
				File image = new File(command[2]);
				if (!image.exists() || !image.isFile() || !image.canRead()) {
					System.out.println("Error: No readable image with given name exists.");
					continue Loop;
				}
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.flush();
				boolean exists = inStream.readBoolean();
				if (!exists) {
					System.out.println("Error: Wine \"" + command[1] + "\" already exists.");
					continue Loop;
				}
				FileInputStream fileIn = new FileInputStream(image);
				outStream.writeLong(image.length());
				byte[] buffer = new byte[1024];
				int read = 0;
				while((read = fileIn.read(buffer)) != -1) {
					outStream.write(buffer, 0, read);
					outStream.flush();
				}
				fileIn.close();
				boolean successful = inStream.readBoolean();
				if (successful) {
					System.out.println("Wine successfully added.");
				}
				else {
					System.out.println("Error: Wine \"" + command[1] + "\" already exists.");
				}
			}
			else if ((command[0].equals("sell") || command[0].equals("s")) && command.length == 4) {
				int quantity;
				int value;
				try {
					quantity = Integer.parseInt(command[3]);
					value = Integer.parseInt(command[2]);
				} catch (NumberFormatException e) {
					System.out.println("Error: Both <value> and <quantity> must be integers.");
					continue Loop;
				}
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeInt(quantity);
				outStream.writeInt(value);
				outStream.flush();
				boolean successful = inStream.readBoolean();
				if (successful) {
					System.out.println("Wine successfully put on sale.");
				}
				else {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
				}
			}
			else if ((command[0].equals("view") || command[0].equals("v")) && command.length == 2) {
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.flush();
				boolean exists = inStream.readBoolean();
				if (!exists) {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
					continue Loop;
				}
				String message = (String) inStream.readObject();
				long size = inStream.readLong();
				FileOutputStream fileOut = new FileOutputStream(command[1] + ".jpg");
				byte[] buffer = new byte [1024];
				int bytes = 0;
				while(size > 0 && (bytes = inStream.read(buffer, 0,
						(int) Math.min(buffer.length, size))) != -1) {
					fileOut.write(buffer, 0, bytes);
					size -= bytes;
				}
				fileOut.close();
				System.out.print(message);
				System.out.println("Image saved to " + command[1] + ".jpg");
			}
			else if ((command[0].equals("buy") || command[0].equals("b")) && command.length == 4) {
				int quantity;
				try {
					quantity = Integer.parseInt(command[3]);
				} catch (NumberFormatException e) {
					System.out.println("Error: <quantity> must be an integer.");
					continue Loop;
				}
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeObject(command[2]);
				outStream.writeInt(quantity);
				outStream.flush();
				int successful = inStream.readInt();
				if (successful == 0) {
					System.out.println("Wine successfully bought.");
				}
				else if (successful == -1) {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
				}
				else if (successful == -2) {
					System.out.println("Error: Atempting to buy more wine than is available for sale.");
				}
				else if (successful == -3) {
					System.out.println("Error: You do not have enough money for this sale.");
				}
			}
			else if ((command[0].equals("wallet") || command[0].equals("w")) && command.length == 1) {
				outStream.writeObject(command[0]);
				outStream.flush();
				int balance = inStream.readInt();
				System.out.println(balance);
			}
			else if ((command[0].equals("classify") || command[0].equals("c")) && command.length == 3) {
				int stars;
				try {
					stars = Integer.parseInt(command[2]);
				} catch (NumberFormatException e) {
					System.out.println("Error: <stars> must be an integer from 1 to 5.");
					continue Loop;
				}
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeInt(stars);
				outStream.flush();
				boolean successful = inStream.readBoolean();
				if (successful) {
					System.out.println("Wine rated successfully.");
				}
				else {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
				}
			}
			else if ((command[0].equals("talk") || command[0].equals("t")) && command.length > 2) {
				StringBuilder message = new StringBuilder();
				for (int i = 2; i < command.length; i++) {
					message.append(command[i]);
				}
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeObject(message.toString());
				outStream.flush();
				boolean successful = inStream.readBoolean();
				if (successful) {
					System.out.println("Message sent successfully.");
				}
				else {
					System.out.println("Error: User \"" + command[1] + "\" does not exist.");
				}
			}
			else if ((command[0].equals("read") || command[0].equals("r")) && command.length == 1) {
				outStream.writeObject(command[0]);
				outStream.flush();
				String messages = (String) inStream.readObject();
				System.out.print(messages);
			}
			else if ((command[0].equals("quit") || command[0].equals("q")) && command.length == 1) {
				System.out.println("Quitting...");
				stop = true;
			}
			else {
				System.out.println("Error: Wrong command or wrong number of arguments.");
			}
		}
		input.close();
		inStream.close();
		outStream.close();
		socket.close();
	}
	
}

















