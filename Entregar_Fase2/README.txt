Grupo 51
    Henrique Catarino - 56278
    Miguel Nunes - 56338
    Vasco Maria - 56374

Compilar:
    javac Tintolmarket.java
    javac TintolmarketServer.java
    javac Wine.java

Jar:
    jar cfe TintolmarketServer.jar TintolmarketServer TintolmarketServer.class TintolmarketServer\$ServerThread.class Wine.class
    jar cfe Tintolmarket.jar Tintolmarket Tintolmarket.class

Executar Servidor:
    java -jar TintolmarketServer.jar [port] <password-cifra> <keystore> <password-keystore>

Executar Cliente:
    java -jar Tintolmarket.jar <IP/hostname>[:port] <truststore> <keystore> <password-keystore> <userID>

Passwords:
    client1.keystore - client1pass
    client2.keystore - client2pass
    server.keystore - server
    certs.truststore - truststore
