package it.patrizio.jobpilot;

import it.patrizio.jobpilot.config.JobPilotProperties;
import it.patrizio.jobpilot.desktop.DesktopLauncher;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/** Punto di ingresso di JobPilot. */
@SpringBootApplication
@EnableConfigurationProperties(JobPilotProperties.class)
@EnableScheduling
public class JobpilotApplication {

    public static void main(String[] args) {
        boolean desktop = "true".equalsIgnoreCase(System.getProperty("jobpilot.desktop"));
        if (desktop && alreadyRunning()) {
            // Un'istanza è già attiva: apriamo solo la finestra e usciamo
            DesktopLauncher.openWindow("http://127.0.0.1:" + System.getProperty("server.port", "8080") + "/");
            return;
        }
        new SpringApplicationBuilder(JobpilotApplication.class)
                .headless(!desktop)   // in modalità desktop serve AWT per l'icona nella tray
                .run(args);
    }

    private static boolean alreadyRunning() {
        try {
            String port = System.getProperty("server.port", "8080");
            HttpURLConnection conn = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/profile").toURL().openConnection();
            conn.setConnectTimeout(800);
            conn.setReadTimeout(1500);
            return conn.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }
}
