import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Le proxy crée un serveur socket qui attendra les connexions sur le port
 * spécifié.
 * Dès qu'une connexion arrive et qu'un socket est accepté, le proxy crée un
 * objet RequestHandler sur un nouveau thread et lui transmet le socket pour
 * qu'il soit traité.Cela permet au Proxy de continuer à accepter d'autres
 * connexions pendant que d'autres sont traitées.
 * 
 * La classe Proxy est également responsable de la gestion dynamique du proxy
 * via la console.
 * et est exécutée sur un thread séparé afin de ne pas interrompre l'acceptation
 * des connexions de socket.
 * Cela permet de bloquer dynamiquement des sites web en temps réel via la
 * console.
 * 
 * Le serveur proxy est également responsable du maintien des copies en cache de
 * tous les sites Web via la console
 * demandé par le client, ce qui inclut les balises HTML, les images, les
 * fichiers css et js associés à chaque page Web.
 * 
 * A la fermeture du serveur mandataire, les HashMaps qui contiennent les
 * éléments en cache et les sites bloqués sont sérialisés et mis à jour.
 * les sites bloqués sont sérialisés et écrits dans un fichier. Ils sont
 * rechargées lorsque le proxy est redémarré, ce qui signifie que les sites mis
 * en cache et bloqués sont maintenus.
 *
 */

public class Proxy implements Runnable {
	private static CertHandler ch;
	/**
	 * Create an instance of Proxy configure certificate, keystore and begin listening for connections
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		ch = new CertHandler();
		ch.mkCert();
		Proxy myProxy = new Proxy(9090);
		myProxy.listen();
	}

	private ServerSocket serverSocket;

	// Semaphore for Proxy and Console Management System.
	private volatile boolean running = true;

	/**
	 * Data structure for constant order lookup of cache items.
	 * Key: URL of page/image requested.
	 * Value: File in storage associated with this key.
	 */
	static HashMap<String, File> cache;

	/**
	 * Data structure for constant order lookup of blocked sites.
	 * Key: URL of page/image requested.
	 * Value: URL of page/image requested.
	 */
	static HashMap<String, String> blockedSites;

	// Running threads
	static ArrayList<Thread> servicingThreads;

	/**	
	 * Create the Proxy Server
	 * 
	 * @param port Port number to run proxy server from.
	 */
	public Proxy(int port) {
		// Load previously cached sites and blocked Sites
		cache = new HashMap<>();
		blockedSites = new HashMap<>();

		// Create array list to hold servicing threads
		servicingThreads = new ArrayList<>();

		// Start dynamic manager on a separate thread.
		new Thread(this).start(); // Starts overriden run() method at bottom

		try {
			// Load in cached sites from file
			File cachedSites = new File("cachedSites.txt");
			if (!cachedSites.exists()) {
				System.out.println("No cached sites found - creating new file");
				cachedSites.createNewFile();
			} else {
				try (FileInputStream fileInputStream = new FileInputStream(cachedSites);
						ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);) {
					cache = (HashMap<String, File>) objectInputStream.readObject();
					fileInputStream.close();
					objectInputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
					cache = new HashMap<>();
				}
			}

			// Load in blocked sites from file
			File blockedSitesTxtFile = new File("blockedSites.txt");
			if (!blockedSitesTxtFile.exists()) {
				System.out.println("No blocked sites found - creating new file");
				blockedSitesTxtFile.createNewFile();
			} else {
				try (FileInputStream fileInputStream = new FileInputStream(blockedSitesTxtFile);
						ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);) {
					blockedSites = (HashMap<String, String>) objectInputStream.readObject();
					fileInputStream.close();
					objectInputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
					blockedSites = new HashMap<>();
				}
			}
		} catch (IOException e) {
			System.out.println("Error loading previously cached sites file");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found loading in previously cached sites file");
			e.printStackTrace();
		}

		try {
			// Create the Server Socket for the Proxy
			serverSocket = new ServerSocket(port);

			// Set timeout for debug
			serverSocket.setSoTimeout(100000);
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
			running = true;
		}

		// Catch exceptions associated with opening socket
		catch (SocketException se) {
			System.out.println("Socket Exception when connecting to client");
			se.printStackTrace();
		} catch (SocketTimeoutException ste) {
			System.out.println("Timeout occured while connecting to client");
		} catch (IOException io) {
			System.out.println("IO exception when connecting to client");
		}
	}

	/**
	 * Listens to port and accepts new socket connections
	 * Creates a new thread to handle the request and passes it to the socket
	 * connection and continues listening
	 */
	public void listen() {
		while (running) {
			try {
				// Blocks until a connection is made
				Socket socket = serverSocket.accept();

				// Create new Thread and pass it Runnable RequestHandler
				Thread thread = new Thread(new RequestHandler(socket, ch));

				// Key a reference to each thread so they can be joined later if necessary
				servicingThreads.add(thread);

				thread.start();
			} catch (SocketException e) {
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Saves the blocked and cached sites to a file so they can be re loaded
	 * Join all of the RequestHandler threads currently servicing requests.
	 */
	private void closeServer() {
		System.out.println("\nClosing Server..");
		running = false;
		try {
			FileOutputStream fileOutputStream = new FileOutputStream("cachedSites.txt");
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

			objectOutputStream.writeObject(cache);
			objectOutputStream.close();
			fileOutputStream.close();
			System.out.println("Cached Sites written");

			FileOutputStream fileOutputStream2 = new FileOutputStream("blockedSites.txt");
			ObjectOutputStream objectOutputStream2 = new ObjectOutputStream(fileOutputStream2);
			objectOutputStream2.writeObject(blockedSites);
			objectOutputStream2.close();
			fileOutputStream2.close();
			System.out.println("Blocked Site list saved");
			try {
				// Close all servicing threads
				for (Thread thread : servicingThreads) {
					if (thread.isAlive()) {
						System.out.print("Waiting on " + thread.getId() + " to close..");
						thread.join();
						System.out.println(" closed");
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			System.out.println("Error saving cache/blocked sites");
			e.printStackTrace();
		}

		// Close Server Socket
		try {
			System.out.println("Terminating Connection");
			serverSocket.close();
		} catch (Exception e) {
			System.out.println("Exception closing proxy's server socket");
			e.printStackTrace();
		}
	}

	/**
	 * Look for File in cache
	 * 
	 * @param url of requested file
	 * @return File if file is cached, null otherwise
	 */
	public static File getCachedPage(String url) {
		return cache.get(url);
	}

	/**
	 * Adds a new page to the cache
	 * 
	 * @param urlString   URL of webpage to cache
	 * @param fileToCache File Object pointing to File put in cache
	 */
	public static void addCachedPage(String urlString, File fileToCache) {
		cache.put(urlString, fileToCache);
	}

	/**
	 * Check if a URL is blocked by the proxy
	 * 
	 * @param url URL to check
	 * @return true if URL is blocked, false otherwise
	 */
	public static boolean isBlocked(String url) {
		if (blockedSites.get(url) != null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Creates a management interface which can dynamically update the proxy
	 * configurations
	 * blocked : Lists currently blocked sites
	 * cached : Lists currently cached sites
	 * close : Closes the proxy server
	 * * : Adds * to the list of blocked sites
	 */
	@Override
	public void run() {
		Scanner scannerCmd = new Scanner(System.in);

		String command;
		while (running) {
			System.out.println(
					"Enter new site to block, or type \"blocked\" to see blocked sites, \"cached\" to see cached sites, or \"close\" or \"Q\" to close server.");
			command = scannerCmd.nextLine();
			if (command.toLowerCase().equals("blocked")) {
				System.out.println("\nCurrently Blocked Sites");
				for (String key : blockedSites.keySet()) {
					System.out.println(key);
				}
				System.out.println();
			} else if (command.toLowerCase().equals("cached")) {
				System.out.println("\nCurrently Cached Sites");
				for (String key : cache.keySet()) {
					System.out.println(key);
				}
				System.out.println();
			} else if (command.equals("close") || command.equals("q") || command.equals("Q")) {
				running = false;
				closeServer();
			} else {
				blockedSites.put(command, command);
				System.out.println("\n" + command + " blocked successfully \n");
			}
		}
		scannerCmd.close();
	}
}
