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
    java -jar TintolmarketServer.jar <port>

Executar Cliente:
    java -jar Tintolmarket.jar <IP/hostname>[:port] <userID> [password]
