import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CertHandler {
    private static String OS = System.getProperty("os.name").toLowerCase();
    private final String mkcertPath = "/home/ltchr/Documents/proxy_tls/java-proxy-server/mkcertLinux";

    public CertHandler() {
    }

    /**
	 * Install the keystore at $HOME/.local/share/mkcert/ for linux user only
	 */
    public void mkCert() {
        if (isOnWin()) {
            System.out.println("Please manually install mkcert or use linux");
        } else {
            runCommand(mkcertPath, "-install");
            //runCommand(mkcertPath, "-CAROOT", "/home/$USER/");
        }
    }

    /**
	 * Generate certificate url 
	 * 
	 * @param urlString parsed urlString
	 */
    public void genCert(String urlString) {
        File file = new File(urlString+".pem");
        if (!file.exists()) {
            System.out.println("Creating cert");
            runCommand(mkcertPath, urlString);    
        } else{
            System.out.println("Using cached certificate");
        }
    }

    
    /** 
     * Run bash command
     * 
     * @param command
     */
    public void runCommand(String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder().command(command);

        try {
            Process process = processBuilder.start();

            // read the output
            InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String output = null;
            while ((output = bufferedReader.readLine()) != null) {
                System.out.println(output);
            }

            // wait for the process to complete
            process.waitFor();

            // close the resources
            bufferedReader.close();
            process.destroy();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    
    /** 
     * Return true if on windows
     * @return boolean
     */
    public boolean isOnWin() {
        boolean isOsWin = false;
        System.out.println(OS);
        if (OS.contains("win")) {
            isOsWin = true;
        } else {
            isOsWin = false;
        }
        return isOsWin;
    }
}
