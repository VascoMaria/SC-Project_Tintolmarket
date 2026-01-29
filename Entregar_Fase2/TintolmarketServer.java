/**
 * @author Grupo 51
 * @author Henrique Catarino - 56278
 * @author Miguel Nunes - 56338
 * @author Vasco Maria - 56374
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class TintolmarketServer {
	
	private static File USERS_FILE;
	private static File WALLETS_FILE;
	private static File WINES_FILE;
	private static File MSG_FILE;
	private static File BLOCK_FILE;
	
	private static File USERS_HASH;
	private static File WALLETS_HASH;
	private static File WINES_HASH;
	private static File MSG_HASH;
	
	private static SecretKey KEY;
	private static AlgorithmParameters PARAMS;
	
	private static PrivateKey SIGNING_KEY;
	
	private static HashMap<String, String> USERS;
	private static HashMap<String, Integer> WALLETS;
	private static HashMap<String, Wine> WINES;
	private static HashMap<String, ArrayList<String>> MESSAGES;
	
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException,
			NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
			InvalidAlgorithmParameterException, CertificateException, SignatureException, KeyStoreException,
			UnrecoverableKeyException {
		int port = 12345;
		if (args.length == 4) {
			port = Integer.parseInt(args[0]);
			System.setProperty("javax.net.ssl.keyStore", args[2]);
			System.setProperty("javax.net.ssl.keyStorePassword", args[3]);
			System.setProperty("javax.net.ssl.keyStoreType", "JCEKS");
			FileInputStream keyStoreFile = new FileInputStream(args[2]);
			KeyStore keyStore = KeyStore.getInstance("JCEKS");
			keyStore.load(keyStoreFile, args[3].toCharArray());
			SIGNING_KEY = (PrivateKey) keyStore.getKey("server", args[3].toCharArray());
			keyStoreFile.close();
		}
		else if (args.length == 3) {
			System.setProperty("javax.net.ssl.keyStore", args[1]);
			System.setProperty("javax.net.ssl.keyStorePassword", args[2]);
			System.setProperty("javax.net.ssl.keyStoreType", "JCEKS");
			FileInputStream keyStoreFile = new FileInputStream(args[1]);
			KeyStore keyStore = KeyStore.getInstance("JCEKS");
			keyStore.load(keyStoreFile, args[2].toCharArray());
			SIGNING_KEY = (PrivateKey) keyStore.getKey("server", args[2].toCharArray());
			keyStoreFile.close();
		}
		else {
			//args error
			System.exit(-1);
		}
		byte[] salt = {(byte) 0xd9, (byte) 0x86, (byte) 0xa5, (byte) 0x49,
				(byte) 0xe5, (byte) 0xbe, (byte) 0xf5, (byte) 0x5f };
		PBEKeySpec keySpec = new PBEKeySpec(args[1].toCharArray(), salt, 20); // pass, salt, iterations
		salt = null; //free memory
		SecretKeyFactory kf = SecretKeyFactory.getInstance("PBEWithHmacSHA256AndAES_128");
		KEY = kf.generateSecret(keySpec);
		kf = null; //free memory
		File paramsFile = new File("params.txt");
		PARAMS = AlgorithmParameters.getInstance("PBEWithHmacSHA256AndAES_128");
		if (paramsFile.exists()) {
			verifyIntegrity(paramsFile, new File("paramsHash.txt"));
			PARAMS.init(Files.readAllBytes(paramsFile.toPath()));
		}
		else {
			paramsFile.createNewFile();
			Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
			c.init(Cipher.ENCRYPT_MODE, KEY);
			c.doFinal("TESTE".getBytes());
			byte[] params = c.getParameters().getEncoded();
			FileOutputStream fos = new FileOutputStream(paramsFile);
			fos.write(params);
			fos.close();
			hash(paramsFile, new File("paramsHash.txt"));
			PARAMS.init(params);
		}
		paramsFile = null; //free memory
		new File("./certs").mkdir();
		USERS_FILE = new File("users.txt");
		USERS_HASH = new File("usersHash.txt");
		if (USERS_FILE.exists()) {
			verifyIntegrity(USERS_FILE, USERS_HASH);
		}
		USERS_FILE.createNewFile();
		USERS_HASH.createNewFile();
		USERS = new HashMap<>();
		byte[] encryptedUsers = Files.readAllBytes(USERS_FILE.toPath());
		if (encryptedUsers.length > 0) {
			Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
			c.init(Cipher.DECRYPT_MODE, KEY, PARAMS);
			String users = new String(c.doFinal(encryptedUsers), StandardCharsets.UTF_8);
			for (String user : users.split("\n")) {
				String[] lineSplit = user.split(":");
				USERS.put(lineSplit[0], lineSplit[1]);
			}
		}
		encryptedUsers = null; //free memory
		BLOCK_FILE = new File("block_1.blk");
		if (BLOCK_FILE.exists()) {
			boolean next = true;
			int id = 1;
			byte[] previousHash = null;
			while (next) {
				BLOCK_FILE = new File("block_" + id + ".blk");
				byte[] block = Files.readAllBytes(BLOCK_FILE.toPath());
				if (id == 1) {
					for (int i = 0; i < 32; i++) {
						if (block[i] != (byte) 0) {
							System.out.println("Error! Hash on block " + id + " could not be verified!");
							System.exit(-1);
						}
					}
				}
				else {
					for (int i = 0; i < 32; i++) {
						if (block[i] != previousHash[i]) {
							System.out.println("Error! Hash on block " + id + " could not be verified!");
							System.exit(-1);
						}
					}
				}
				int offset = 32;
				long blk_id = 0;
				for (int i = offset; i < offset + 8; i++) {
					blk_id = (blk_id << 8) + (block[i] & 0xFF);
				}
				if (blk_id != id) {
					System.out.println("Error! blk_id of block " + id + " could not be verified");
					System.exit(-1);
				}
				offset += 8;
				long n_trx = 0;
				for (int i = offset; i < offset + 8; i++) {
					n_trx = (n_trx << 8) + (block[i] & 0xFF);
				}
				offset += 8;
				for (int i = 0; i < n_trx; i++) {
					int trxSize = 0;
					for (int f = offset; f < offset + 4; f++) {
						trxSize = (trxSize << 8) + (block[f] & 0xFF);
					}
					offset += 4;
					byte[] trxBytes = new byte[trxSize];
					for (int f = offset; f < offset + trxSize; f++) {
						trxBytes[f - offset] = block[f];
					}
					offset += trxSize;
					int sigSize = 0;
					for (int f = offset; f < offset + 4; f++) {
						sigSize = (sigSize << 8) + (block[f] & 0xFF);
					}
					offset += 4;
					byte[] sigBytes = new byte[sigSize];
					for (int f = offset; f < offset + sigSize; f++) {
						sigBytes[f - offset] = block[f];
					}
					offset += sigSize;
					String trx = new String(trxBytes, StandardCharsets.UTF_8);
					String user_id = trx.split(" ")[4];
					FileInputStream certInput = new FileInputStream(USERS.get(user_id));
					CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
					Certificate certificate = certFactory.generateCertificate(certInput);
					certInput.close();
					Signature signature = Signature.getInstance("MD5withRSA");
					signature.initVerify(certificate);
					signature.update(trxBytes);
					if (!signature.verify(sigBytes)) {
						System.out.println("Error! Transaction " + i + " of block " + id + " could not be verified");
						System.exit(-1);
					}
				}
				if (n_trx == 5) {
					byte[] blockOnly = new byte[offset];
					for (int i = 0; i < offset; i++) {
						blockOnly[i] = block[i];
					}
					byte[] sigBytes = new byte[block.length - blockOnly.length];
					for (int f = offset; f < block.length; f++) {
						sigBytes[f - offset] = block[f];
					}
					Certificate certificate;
					if (args.length == 4) {
						FileInputStream keyStoreFile = new FileInputStream(args[2]);
						KeyStore keyStore = KeyStore.getInstance("JCEKS");
						keyStore.load(keyStoreFile, args[3].toCharArray());
						certificate = keyStore.getCertificate("server");
						keyStoreFile.close();
					}
					else {
						FileInputStream keyStoreFile = new FileInputStream(args[1]);
						KeyStore keyStore = KeyStore.getInstance("JCEKS");
						keyStore.load(keyStoreFile, args[2].toCharArray());
						certificate = keyStore.getCertificate("server");
						keyStoreFile.close();
					}
					Signature signature = Signature.getInstance("MD5withRSA");
					signature.initVerify(certificate);
					signature.update(blockOnly);
					if (!signature.verify(sigBytes)) {
						System.out.println("Error! Block " + id + " could not be verified");
						System.exit(-1);
					}
					MessageDigest digest = MessageDigest.getInstance("SHA-256");
					digest.update(block);
					previousHash = digest.digest();
					id++;
				}
				else {
					next = false;
				}
			}
		}
		else {
			ArrayList<Byte> list = new ArrayList<>();
			for (int i = 0; i < 32; i++) {
				list.add((byte) 0);
			}
			byte[] blk_id = ByteBuffer.allocate(8).putLong(1).array();
			for (int i = 0; i < 8; i++) {
				list.add(blk_id[i]);
			}
			byte[] n_trx = ByteBuffer.allocate(8).putLong(0).array();
			for (int i = 0; i < 8; i++) {
				list.add(n_trx[i]);
			}
			FileOutputStream blockOut = new FileOutputStream(BLOCK_FILE);
			Object[] block1 = list.toArray();
			for (int i = 0; i < block1.length; i++) {
				blockOut.write(((Byte)block1[i]).byteValue());
			}
			blockOut.close();
		}
		WALLETS_FILE = new File("wallets.txt");
		WALLETS_HASH = new File("walletsHash.txt");
		if (WALLETS_FILE.exists()) {
			verifyIntegrity(WALLETS_FILE, WALLETS_HASH);
		}
		WALLETS_FILE.createNewFile();
		WALLETS_HASH.createNewFile();
		WALLETS = new HashMap<>();
		BufferedReader buffReader = new BufferedReader(new FileReader(WALLETS_FILE));
		String line;
		while((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(":");
			WALLETS.put(lineSplit[0], Integer.parseInt(lineSplit[1]));
		}
		buffReader.close();
		WINES_FILE = new File("wines.txt");
		WINES_HASH = new File("winesHash.txt");
		if (WINES_FILE.exists()) {
			verifyIntegrity(WINES_FILE, WINES_HASH);
		}
		WINES_FILE.createNewFile();
		WINES_HASH.createNewFile();
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
		MSG_HASH = new File("messagesHash.txt");
		if (MSG_FILE.exists()) {
			verifyIntegrity(MSG_FILE, MSG_HASH);
		}
		MSG_FILE.createNewFile();
		MSG_HASH.createNewFile();
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
		hash(USERS_FILE, USERS_HASH);
		hash(WALLETS_FILE, WALLETS_HASH);
		hash(WINES_FILE, WINES_HASH);
		hash(MSG_FILE, MSG_HASH);
		TintolmarketServer server = new TintolmarketServer();
		server.startServer(port);
	}

	private synchronized static void verifyIntegrity(File file, File hashFile) throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(Files.readAllBytes(file.toPath()));
		if (!hashFile.exists() || !MessageDigest.isEqual(digest.digest(), Files.readAllBytes(hashFile.toPath()))) {
			System.out.println("Error! The integrity of file \"" + file.toPath().toString() + "\" could not be verified!");
			System.exit(-1);
		}
	}
	
	private synchronized static void hash(File file, File hashFile) throws NoSuchAlgorithmException, IOException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(Files.readAllBytes(file.toPath()));
		byte[] hash = digest.digest();
		FileOutputStream fileOut = new FileOutputStream(hashFile);
		for (int i = 0; i < hash.length; i++) {
			fileOut.write(hash[i]);
		}
		fileOut.close();
	}
	
	public void startServer(int port) {
		ServerSocketFactory sSocFact = SSLServerSocketFactory.getDefault();
		SSLServerSocket sSoc = null;
		try {
			sSoc = (SSLServerSocket) sSocFact.createServerSocket(port);
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
		    	//connection ended; ignore
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
				byte[] nonce = new byte[8];
				SecureRandom random = new SecureRandom();
				random.nextBytes(nonce);
				for (int i = 0; i < 8; i++) {
					outStream.writeByte(nonce[i]);
				}
				byte[] clientNonce = null;
				byte[] signedNonce = null;
				byte[] encodedCert = null;
				if (!userExists(user)) {
					outStream.writeBoolean(false);
					outStream.flush();
					clientNonce = new byte[8];
					for (int i = 0; i < 8; i++) {
						clientNonce[i] = inStream.readByte();
					}
					signedNonce = new byte[inStream.readInt()];
					for (int i = 0; i < signedNonce.length; i++) {
						signedNonce[i] = inStream.readByte();
					}
					encodedCert = new byte[inStream.readInt()];
					for (int i = 0; i < encodedCert.length; i++) {
						encodedCert[i] = inStream.readByte();
					}
				}
				else {
					outStream.writeBoolean(true);
					outStream.flush();
					signedNonce = new byte[inStream.readInt()];
					for (int i = 0; i < signedNonce.length; i++) {
						signedNonce[i] = inStream.readByte();
					}
				}
				if (!authenticate(user, nonce, clientNonce, signedNonce, encodedCert)) {
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
						String wine = (String) inStream.readObject();
						int quantity = inStream.readInt();
						int value = inStream.readInt();
						String transaction = (String) inStream.readObject();
						int signedTrxSize = inStream.readInt();
						byte[] signedTrx = new byte[signedTrxSize];
						for (int i = 0; i < signedTrxSize; i++) {
							signedTrx[i] = inStream.readByte();
						}
						FileInputStream certInput = new FileInputStream(USERS.get(user));
						CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
						Certificate certificate = certFactory.generateCertificate(certInput);
						certInput.close();
						Signature signature = Signature.getInstance("MD5withRSA");
						signature.initVerify(certificate);
						signature.update(transaction.getBytes());
						if (!signature.verify(signedTrx)) {
							outStream.writeInt(-4);
						}
						else {
							outStream.writeInt(sell(user, wine, quantity, value, transaction, signedTrx));
						}
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
						String wine = (String) inStream.readObject();
						String seller = (String) inStream.readObject();
						int quantity = inStream.readInt();
						outStream.writeInt(value(wine, seller));
						outStream.flush();
						String transaction = (String) inStream.readObject();
						int sigSize = inStream.readInt();
						byte[] signedTrx = new byte[sigSize];
						for (int i = 0; i < sigSize; i++) {
							signedTrx[i] = inStream.readByte();
						}
						FileInputStream certInput = new FileInputStream(USERS.get(user));
						CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
						Certificate certificate = certFactory.generateCertificate(certInput);
						certInput.close();
						Signature signature = Signature.getInstance("MD5withRSA");
						signature.initVerify(certificate);
						signature.update(transaction.getBytes());
						if (!signature.verify(signedTrx)) {
							outStream.writeInt(-4);
						}
						else {
							outStream.writeInt(buy(user, wine, seller, quantity, transaction, signedTrx));
						}
					}
					else if (command.equals("wallet") || command.equals("w")) {
						outStream.writeInt(viewBalance(user));
					}
					else if (command.equals("classify") || command.equals("c")) {
						boolean success = classify((String) inStream.readObject(), inStream.readInt());
						outStream.writeBoolean(success);
					}
					else if (command.equals("talk") || command.equals("t")) {
						String username = (String) inStream.readObject();
						int size = inStream.readInt();
						byte[] encrypted = new byte[size];
						for (int i = 0; i < size; i++) {
							encrypted[i] = inStream.readByte();
						}
						boolean success = saveMessage(user, username, encrypted);
						outStream.writeBoolean(success);
					}
					else if (command.equals("read") || command.equals("r")) {
						String messages = getMessages(user);
						outStream.writeObject(messages);
					}
					else if (command.equals("list") || command.equals("l")) {
						String transactions = listTransactions();
						outStream.writeObject(transactions);
					}
					outStream.flush();
				}
				outStream.close();
				inStream.close();
				socket.close();
			} catch (IOException | ClassNotFoundException | CertificateException |
					InvalidKeyException | NoSuchAlgorithmException | SignatureException |
					NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException |
					BadPaddingException e) {
				e.printStackTrace();
			}
		}
 
	}
	
	private synchronized boolean userExists(String user) {
		return USERS.containsKey(user);
	}
	
	private synchronized boolean authenticate(String user, byte[] nonce, byte[] clientNonce,
			byte[] signedNonce, byte[] encodedCert) throws IOException, CertificateException,
			NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchPaddingException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
		if (userExists(user)) {
			FileInputStream certInput = new FileInputStream(USERS.get(user));
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			Certificate certificate = certFactory.generateCertificate(certInput);
			certInput.close();
			Signature signature = Signature.getInstance("MD5withRSA");
			signature.initVerify(certificate);
			signature.update(nonce);
			return signature.verify(signedNonce);
		}
		else {
			verifyIntegrity(USERS_FILE, USERS_HASH);
			verifyIntegrity(WALLETS_FILE, WALLETS_HASH);
			for (int i = 0; i < 8; i++) {
				if (nonce[i] != clientNonce[i]) {
					return false;
				}
			}
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			Certificate certificate = certFactory.generateCertificate(new ByteArrayInputStream(encodedCert));
			Signature signature = Signature.getInstance("MD5withRSA");
			signature.initVerify(certificate);
			signature.update(nonce);
			if (!signature.verify(signedNonce)) {
				return false;
			}
			File certFile = new File("./certs/" + user + ".cer");
			certFile.createNewFile();
			FileOutputStream certOut = new FileOutputStream(certFile);
			for (int i = 0; i < encodedCert.length; i++) {
				certOut.write(encodedCert[i]);
			}
			certOut.close();
			USERS.put(user, "./certs/" + user + ".cer");
			byte[] encryptedUsers = Files.readAllBytes(USERS_FILE.toPath());
			String users = "";
			if (encryptedUsers.length > 0) {
				Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
				c.init(Cipher.DECRYPT_MODE, KEY, PARAMS);
				users = new String(c.doFinal(encryptedUsers), StandardCharsets.UTF_8);
			}
			users += user + ":./certs/" + user + ".cer\n";
			Cipher c = Cipher.getInstance("PBEWithHmacSHA256AndAES_128");
			c.init(Cipher.ENCRYPT_MODE, KEY, PARAMS);
			encryptedUsers = c.doFinal(users.getBytes());
			FileOutputStream usersOut = new FileOutputStream(USERS_FILE);
			for (int i = 0; i < encryptedUsers.length; i++) {
				usersOut.write(encryptedUsers[i]);
			}
			usersOut.close();
			WALLETS.put(user, 200);
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WALLETS_FILE, true));
			buffWriter.write(user + ":200\n");
			buffWriter.close();
			hash(USERS_FILE, USERS_HASH);
			hash(WALLETS_FILE, WALLETS_HASH);
			return true;
		}
	}
	
	private synchronized void transaction(String transaction, byte[] signedTrx) throws IOException,
			NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		byte[] trxBytes = transaction.getBytes();
		byte[] block = Files.readAllBytes(BLOCK_FILE.toPath());
		long n_trx = 0;
		for (int i = 40; i < 48; i++) {
			n_trx = (n_trx << 8) + (block[i] & 0xFF);
		}
		n_trx++;
		byte[] newBlock = new byte[block.length + 4 + trxBytes.length + 4 + signedTrx.length];
		for (int i = 0; i < 40; i++) {
			newBlock[i] = block[i];
		}
		int offset = 40;
		byte[] n_trx_bytes = ByteBuffer.allocate(8).putLong(n_trx).array();
		for (int i = offset; i < offset + 8; i++) {
			newBlock[i] = n_trx_bytes[i - offset];
		}
		offset += 8;
		for (int i = offset; i < block.length; i++) {
			newBlock[i] = block[i];
		}
		offset = block.length;
		byte[] trxSize = ByteBuffer.allocate(4).putInt(trxBytes.length).array();
		for (int i = offset; i < offset + 4; i++) {
			newBlock[i] = trxSize[i - offset];
		}
		offset += 4;
		for (int i = offset; i < offset + trxBytes.length; i++) {
			newBlock[i] = trxBytes[i - offset];
		}
		offset += trxBytes.length;
		byte[] sigSizeBytes = ByteBuffer.allocate(4).putInt(signedTrx.length).array();
		for (int i = offset; i < offset + 4; i++) {
			newBlock[i] = sigSizeBytes[i - offset];
		}
		offset += 4;
		for (int i = offset; i < offset + signedTrx.length; i++) {
			newBlock[i] = signedTrx[i - offset];
		}
		if (n_trx == 5) {
			Signature signature = Signature.getInstance("MD5withRSA");
			signature.initSign(SIGNING_KEY);
			signature.update(newBlock);
			byte[] newBlockSig = signature.sign();
			byte[] signedNewBlock = new byte[newBlock.length + newBlockSig.length];
			for (int i = 0; i < newBlock.length; i++) {
				signedNewBlock[i] = newBlock[i];
			}
			for (int i = newBlock.length; i < newBlock.length + newBlockSig.length; i++) {
				signedNewBlock[i] = newBlockSig[i - newBlock.length];
			}
			newBlock = signedNewBlock;
		}
		FileOutputStream outBlock = new FileOutputStream(BLOCK_FILE);
		for (int i = 0; i < newBlock.length; i++) {
			outBlock.write(newBlock[i]);
		}
		outBlock.close();
		if (n_trx == 5) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(newBlock);
			byte[] hash = digest.digest();
			long blk_id = 0;
			for (int i = 32; i < 40; i++) {
				blk_id = (blk_id << 8) + (block[i] & 0xFF);
			}
			blk_id++;
			BLOCK_FILE = new File("block_" + blk_id + ".blk");
			BLOCK_FILE.createNewFile();
			outBlock = new FileOutputStream(BLOCK_FILE);
			for (int i = 0; i < hash.length; i++) {
				outBlock.write(hash[i]);
			}
			byte[] blk_id_bytes = ByteBuffer.allocate(8).putLong(blk_id).array();
			for (int i = 0; i < blk_id_bytes.length; i++) {
				outBlock.write(blk_id_bytes[i]);
			}
			for (int i = 0; i < 8; i++) {
				outBlock.write((byte) 0);
			}
			outBlock.close();
		}
	}
	
	private synchronized boolean add(String wineName, File imagem) throws IOException, NoSuchAlgorithmException {
		if (WINES.containsKey(wineName)) {
			return false ;
		}
		else {
			verifyIntegrity(WINES_FILE, WINES_HASH);
			WINES.put(wineName, new Wine(wineName, imagem));
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(WINES_FILE, true));
			buffWriter.write(wineName + ":" + imagem.getPath() + ":0:0\n");
			buffWriter.close();
			hash(WINES_FILE, WINES_HASH);
			return true;
		}
	}
	
	private synchronized int value(String wineName, String seller) {
		Wine wine = WINES.get(wineName);
		if (wine == null) {
			return -1;
		}
		ArrayList<Integer> list = wine.getSale(seller);
		if (list == null) {
			return -2;
		}
		return list.get(1);
	}
	
	private synchronized int sell(String user, String wineName, int quantity, int value, String transaction, 
			byte[] signedTrx) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		if (WINES.containsKey(wineName)) {
			verifyIntegrity(WINES_FILE, WINES_HASH);
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
			hash(WINES_FILE, WINES_HASH);
			transaction(transaction, signedTrx);
			return 0;
		} else {
			return -1;
		}
	}

	private synchronized int buy(String user, String wine, String seller, int quantity, String transaction,
			byte[] signedTrx) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		if (WINES.containsKey(wine)) {
			if (WINES.get(wine).getSale(seller).get(0) >= quantity) {
				if (WINES.get(wine).getSale(seller).get(1) * quantity <= WALLETS.get(user) ) {
					int value = WINES.get(wine).getSale(seller).get(1);
					changeBalance(seller, quantity, value);
					changeBalance(user, -quantity, value);
					WINES.get(wine).buyWine(seller, quantity);
					changeQuantity(seller, quantity);
					transaction(transaction, signedTrx);
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
	
	private synchronized void changeBalance(String username, int quantity, int value) throws IOException,
			NoSuchAlgorithmException {
		verifyIntegrity(WALLETS_FILE, WALLETS_HASH);
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
		hash(WALLETS_FILE, WALLETS_HASH);
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
	
	private synchronized boolean classify(String wineName, int stars) throws IOException, NoSuchAlgorithmException {
		Wine wine = WINES.get(wineName);
		if (wine != null) {
			verifyIntegrity(WINES_FILE, WINES_HASH);
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
			hash(WINES_FILE, WINES_HASH);
			return true;
		}
		return false;
	}
	
	private synchronized boolean saveMessage(String user, String username, byte[] encrypted) throws IOException,
			NoSuchAlgorithmException {
		if (!userExists(username)) {
			return false;
		}
		ArrayList<String> messages = MESSAGES.get(username);
		if (messages == null) {
			messages = new ArrayList<>();
		}
		String encryptedString = Base64.getEncoder().encodeToString(encrypted);
		messages.add(user + ":" + encryptedString);
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
		hash(MSG_FILE, MSG_HASH);
		return true;
	}
	
	private synchronized String getMessages(String user) throws IOException, NoSuchAlgorithmException {
		StringBuilder messages = new StringBuilder("");
		verifyIntegrity(MSG_FILE, MSG_HASH);
		BufferedReader buffReader = new BufferedReader(new FileReader(MSG_FILE));
		StringBuilder lines = new StringBuilder();
		String line;
		while ((line = buffReader.readLine()) != null) {
			String[] lineSplit = line.split(";");
			if (lineSplit[0].equals(user)) {
				for (int i = 1; i < lineSplit.length - 1; i++) {
					messages.append(lineSplit[i] + ";");
				}
				messages.append(lineSplit[lineSplit.length - 1]);
			}
			else {
				lines.append(line + "\n");
			}
		}
		buffReader.close();
		BufferedWriter buffWriter = new BufferedWriter(new FileWriter(MSG_FILE));
		buffWriter.write(lines.toString());
		buffWriter.close();
		hash(MSG_FILE, MSG_HASH);
		return messages.toString();
	}
	
	private synchronized void changeQuantity(String username, int quantity) throws IOException, NoSuchAlgorithmException {
		verifyIntegrity(WINES_FILE, WINES_HASH);
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
		hash(WINES_FILE, WINES_HASH);
	}

	private synchronized String listTransactions() throws IOException, CertificateException,
			NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		StringBuilder transactions = new StringBuilder("");
		boolean next = true;
		int id = 1;
		while (next) {
			File blockFile = new File("block_" + id + ".blk");
			byte[] block = Files.readAllBytes(blockFile.toPath());
			int offset = 40;
			long n_trx = 0;
			for (int i = offset; i < offset + 8; i++) {
				n_trx = (n_trx << 8) + (block[i] & 0xFF);
			}
			offset += 8;
			for (int i = 0; i < n_trx; i++) {
				int trxSize = 0;
				for (int f = offset; f < offset + 4; f++) {
					trxSize = (trxSize << 8) + (block[f] & 0xFF);
				}
				offset += 4;
				byte[] trxBytes = new byte[trxSize];
				for (int f = offset; f < offset + trxSize; f++) {
					trxBytes[f - offset] = block[f];
				}
				offset += trxSize;
				int sigSize = 0;
				for (int f = offset; f < offset + 4; f++) {
					sigSize = (sigSize << 8) + (block[f] & 0xFF);
				}
				offset += 4;
				offset += sigSize;
				String trx = new String(trxBytes, StandardCharsets.UTF_8);
				transactions.append(trx + "\n");
			}
			if (n_trx == 5) {
				id++;
			}
			else {
				next = false;
			}
		}
		return transactions.toString();
	}

}