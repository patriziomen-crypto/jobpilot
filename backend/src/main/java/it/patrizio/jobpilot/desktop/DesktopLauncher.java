package it.patrizio.jobpilot.desktop;

import it.patrizio.jobpilot.config.JobPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Modalità desktop (attiva con jobpilot.desktop=true, impostata dal pacchetto JobPilot.exe):
 * all'avvio apre l'app in una finestra del browser senza barra degli indirizzi e mette un'icona
 * nell'area di notifica con il menu Apri / Apri cartella PDF / Esci.
 */
@Component
@ConditionalOnProperty(name = "jobpilot.desktop", havingValue = "true")
public class DesktopLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    private final Environment environment;
    private final JobPilotProperties properties;
    private final ConfigurableApplicationContext context;

    public DesktopLauncher(Environment environment, JobPilotProperties properties, ConfigurableApplicationContext context) {
        this.environment = environment;
        this.properties = properties;
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String url = "http://127.0.0.1:" + environment.getProperty("local.server.port", "8080") + "/";
        installTrayIcon(url);
        openWindow(url);
    }

    private void installTrayIcon(String url) {
        if (!SystemTray.isSupported()) {
            log.warn("Area di notifica non disponibile: nessuna icona nella tray");
            return;
        }
        try {
            Image image = loadIcon();
            PopupMenu menu = new PopupMenu();

            MenuItem apri = new MenuItem("Apri JobPilot");
            apri.addActionListener(e -> openWindow(url));
            menu.add(apri);

            MenuItem cartella = new MenuItem("Apri cartella dei PDF");
            cartella.addActionListener(e -> openFolder(properties.outputDir()));
            menu.add(cartella);

            TrayIcon trayIcon = new TrayIcon(image, "JobPilot - " + url, menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> openWindow(url)); // doppio click sull'icona

            menu.addSeparator();
            MenuItem esci = new MenuItem("Esci");
            esci.addActionListener(e -> {
                SystemTray.getSystemTray().remove(trayIcon);
                int code = SpringApplication.exit(context, () -> 0);
                System.exit(code);
            });
            menu.add(esci);

            SystemTray.getSystemTray().add(trayIcon);
            trayIcon.displayMessage("JobPilot", "In esecuzione su " + url, TrayIcon.MessageType.INFO);
        } catch (Exception e) {
            log.warn("Icona nella tray non creata: {}", e.getMessage());
        }
    }

    private Image loadIcon() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/icons/jobpilot.png")) {
            if (in == null) {
                throw new IOException("icona /icons/jobpilot.png non trovata");
            }
            return ImageIO.read(in);
        }
    }

    /** Apre l'app come finestra "app" di Edge o Chrome (senza barra URL); altrimenti nel browser predefinito. */
    public static void openWindow(String url) {
        List<String> browsers = List.of(
                System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe");
        for (String browser : browsers) {
            if (browser != null && Files.exists(Path.of(browser))) {
                try {
                    new ProcessBuilder(browser, "--app=" + url, "--window-size=1440,900").start();
                    return;
                } catch (IOException e) {
                    log.debug("Browser {} non avviabile: {}", browser, e.getMessage());
                }
            }
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            log.warn("Impossibile aprire il browser: apri manualmente {}", url);
        }
    }

    private static void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            new ProcessBuilder("explorer.exe", folder.toAbsolutePath().toString()).start();
        } catch (IOException e) {
            log.warn("Impossibile aprire la cartella {}: {}", folder, e.getMessage());
        }
    }
}
