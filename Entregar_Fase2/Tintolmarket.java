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
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Tintolmarket {
	
	public static void main(String[] args) throws IOException, ClassNotFoundException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, InvalidKeyException,
			SignatureException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		if (args.length != 5) {
			System.out.println("Wrong format used. Please use \"Tintolmarket "
					+ "<IP/hostname>[:port] <truststore> <keystore> <password-keystore> <userID>\"");
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
						+ "<IP/hostname>[:port] <truststore> <keystore> <password-keystore> <userID>\"");
				System.exit(-1);
			}
		}
		String user = args[4];
		System.setProperty("javax.net.ssl.trustStore", args[1]);
		System.setProperty("javax.net.ssl.trustStorePassword", "truststore");
		System.setProperty("javax.net.ssl.trustStoreType", "JCEKS");
		SocketFactory socFact = SSLSocketFactory.getDefault();
		SSLSocket socket = (SSLSocket) socFact.createSocket(host, port);
		FileInputStream keyStoreFile = new FileInputStream(args[2]);
		KeyStore keyStore = KeyStore.getInstance("JCEKS");
		keyStore.load(keyStoreFile, args[3].toCharArray());
		PrivateKey key = (PrivateKey) keyStore.getKey(user, args[3].toCharArray());
		Certificate certificate = keyStore.getCertificate(user);
		ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
		ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
		outStream.writeObject(user);
		outStream.flush();
		byte[] nonce = new byte[8];
		for (int i = 0; i < 8; i++) {
			nonce[i] = inStream.readByte();
		}
		boolean known = inStream.readBoolean();
		Signature signature = Signature.getInstance("MD5withRSA");
		signature.initSign(key);
		signature.update(nonce);
		byte[] signedNonce = signature.sign();
		if (!known) {
			for (int i = 0; i < 8; i++) {
				outStream.writeByte(nonce[i]);
			}
			outStream.writeInt(signedNonce.length);
			for (int i = 0; i < signedNonce.length; i++) {
				outStream.writeByte(signedNonce[i]);
			}
			byte[] encodedCert = certificate.getEncoded();
			outStream.writeInt(encodedCert.length);
			for (int i = 0; i < encodedCert.length; i++) {
				outStream.writeByte(encodedCert[i]);
			}
		}
		else {
			outStream.writeInt(signedNonce.length);
			for (int i = 0; i < signedNonce.length; i++) {
				outStream.writeByte(signedNonce[i]);
			}
		}
		outStream.flush();
		boolean authenticated = inStream.readBoolean();
		if (!authenticated) {
			System.out.println("Authentication error!");
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
		Scanner input = new Scanner(System.in);
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
				String transaction = "sell " + command[1] + " " + quantity + " " + value + " " + user;
				signature.update(transaction.getBytes());
				byte[] signedTrx = signature.sign();
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeInt(quantity);
				outStream.writeInt(value);
				outStream.writeObject(transaction);
				outStream.writeInt(signedTrx.length);
				for (int i = 0; i < signedTrx.length; i++) {
					outStream.writeByte(signedTrx[i]);
				}
				outStream.flush();
				int successful = inStream.readInt();
				if (successful == 0) {
					System.out.println("Wine successfully put on sale.");
				}
				else if (successful == -1) {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
				}
				else {
					System.out.println("Error: Signature could not be verified.");
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
				int value = inStream.readInt();
				if (value == -1) {
					System.out.println("Error: Wine \"" + command[1] + "\" does not exist.");
					continue Loop;
				}
				else if (value == -2) {
					System.out.println("Error: Atempting to buy more wine than is available for sale.");
					continue Loop;
				}
				String transaction = "buy " +  command[1] + " " + quantity + " " + value + " " + user;
				signature.update(transaction.getBytes());
				byte[] signedTrx = signature.sign();
				outStream.writeObject(transaction);
				outStream.writeInt(signedTrx.length);
				for (int i = 0; i < signedTrx.length; i++) {
					outStream.writeByte(signedTrx[i]);
				}
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
				else {
					System.out.println("Error: Signature could not be verified.");
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
				for (int i = 2; i < command.length - 1; i++) {
					message.append(command[i] + " ");
				}
				message.append(command[command.length - 1]);
				FileInputStream trustStoreFile = new FileInputStream(args[1]);
				KeyStore trustStore = KeyStore.getInstance("JCEKS");
				trustStore.load(trustStoreFile, "truststore".toCharArray());
				Certificate destCertificate = trustStore.getCertificate(command[1]);
				PublicKey destKey = destCertificate.getPublicKey();
				Cipher cipher = Cipher.getInstance("RSA");
				cipher.init(Cipher.ENCRYPT_MODE, destKey);
				byte[] encryptedMessage = cipher.doFinal(message.toString().getBytes());
				outStream.writeObject(command[0]);
				outStream.writeObject(command[1]);
				outStream.writeInt(encryptedMessage.length);
				for (int i = 0; i < encryptedMessage.length; i++) {
					outStream.writeByte(encryptedMessage[i]);
				}
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
				if (messages.equals("")) {
					System.out.println("No Messages!");
					continue Loop;
				}
				for (String message : messages.split(";")) {
					String[] messageSplit = message.split(":");
					byte[] encryptedMessage = Base64.getDecoder().decode(messageSplit[1]);
					Cipher cipher = Cipher.getInstance("RSA");
					cipher.init(Cipher.DECRYPT_MODE, key);
					byte[] decryptedMessageBytes = cipher.doFinal(encryptedMessage);
					String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);
					System.out.println("From " + messageSplit[0] + ":\n" + decryptedMessage + "\n");
				}
			}
			else if ((command[0].equals("list") || command[0].equals("l")) && command.length == 1) {
				outStream.writeObject(command[0]);
				outStream.flush();
				String transactions = (String) inStream.readObject();
				if (transactions.equals("")) {
					System.out.println("No transactions recorded.");
				}
				else {
					System.out.print(transactions);
				}
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
