package com.dungeonscore.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Serves the resourcepack.zip bundled in the jar (built by resourcepack/generate_pack.py)
 * and sends it to players when they join.
 */
public final class PackService implements Listener {

    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("dungeonscore:resourcepack".getBytes(StandardCharsets.UTF_8));
    private static final String PATH = "/DungeonsPack.zip";
    private static final String ITEMS_PREFIX = "assets/dungeons/items/";

    private final JavaPlugin plugin;
    private final Logger log;

    private byte[] zip;
    private String sha1;
    private HttpServer server;
    private ExecutorService executor;
    private ResourcePackRequest request;

    public PackService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void start() {
        FileConfiguration cfg = plugin.getConfig();
        if (!loadZip()) {
            return;
        }
        if (!cfg.getBoolean("resource-pack.enabled", true)) {
            log.info("Resource pack sending is disabled in config.yml.");
            return;
        }

        URI uri;
        String external = cfg.getString("resource-pack.external-url", "").trim();
        if (!external.isEmpty()) {
            uri = URI.create(external);
            log.info("Using external resource pack URL " + uri
                    + " - that file must have SHA-1 " + sha1);
        } else {
            int port = cfg.getInt("resource-pack.port", 8164);
            if (!startServer(port)) {
                return;
            }
            String host = cfg.getString("resource-pack.public-address", "").trim();
            if (host.isEmpty()) {
                host = Bukkit.getIp();
            }
            if (host == null || host.isBlank()) {
                host = "127.0.0.1";
                log.warning("No resource-pack.public-address set - the pack URL uses 127.0.0.1, "
                        + "which only works for players on this machine.");
            }
            uri = URI.create("http://" + host + ":" + port + PATH + "?v=" + sha1.substring(0, 8));
            log.info("Hosting resource pack at " + uri);
        }

        request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
                .required(cfg.getBoolean("resource-pack.required", false))
                .prompt(Component.text(cfg.getString("resource-pack.prompt", "")))
                .replace(false)
                .build();
    }

    private boolean loadZip() {
        try (InputStream in = plugin.getResource("resourcepack.zip")) {
            if (in == null) {
                log.severe("resourcepack.zip is missing from the jar - run resourcepack/generate_pack.py "
                        + "and rebuild the plugin.");
                return false;
            }
            zip = in.readAllBytes();
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            log.info("Resource pack: " + zip.length / 1024 + " KB, SHA-1 " + sha1);
            return true;
        } catch (IOException | NoSuchAlgorithmException e) {
            log.severe("Could not read resourcepack.zip: " + e);
            return false;
        }
    }

    private boolean startServer(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            log.severe("Could not start the resource pack web server on port " + port + ": " + e.getMessage()
                    + " - change resource-pack.port or set resource-pack.external-url.");
            return false;
        }
        server.createContext(PATH, this::handle);
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DungeonsCore-PackServer");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.start();
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            if (method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(zip.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(zip);
            }
        } finally {
            exchange.close();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** @return false if the pack is unavailable (disabled or failed to start). */
    public boolean send(Player player) {
        if (request == null) {
            return false;
        }
        player.sendResourcePacks(request);
        return true;
    }

    public String sha1() {
        return sha1;
    }

    /** Warns about ids that exist in the plugin but not the pack (purple/black item) or vice versa. */
    public void checkItems(Collection<String> ids) {
        if (zip == null) {
            return;
        }
        Set<String> inPack = new TreeSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(ITEMS_PREFIX) && name.endsWith(".json")) {
                    inPack.add(name.substring(ITEMS_PREFIX.length(), name.length() - 5));
                }
            }
        } catch (IOException e) {
            log.warning("Could not inspect resourcepack.zip: " + e);
            return;
        }
        Set<String> missing = new TreeSet<>(ids);
        missing.removeAll(inPack);
        Set<String> unused = new TreeSet<>(inPack);
        unused.removeAll(ids);
        if (!missing.isEmpty()) {
            log.warning("Items with no model in the resource pack: " + missing
                    + " - add them to generate_pack.py and rebuild.");
        }
        if (!unused.isEmpty()) {
            log.warning("Resource pack models with no plugin item: " + unused);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        switch (status) {
            case DECLINED -> log.info(event.getPlayer().getName() + " declined the resource pack.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> log.warning(event.getPlayer().getName()
                    + " could not load the resource pack (" + status + "). Check that the pack URL is "
                    + "reachable from their machine.");
            default -> { }
        }
    }
}
