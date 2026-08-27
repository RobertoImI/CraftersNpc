package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.skin.SkinDirectory;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinTextureManager {
    private static final int MAX_DOWNLOAD_BYTES = 1024 * 1024;
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> URL_REQUESTS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ResourceLocation DEFAULT_STEVE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private SkinTextureManager() {
    }

    public static ResourceLocation resolveTexture(String skinId) {
        String clean = skinId == null || skinId.isBlank() ? "steve" : skinId.toLowerCase(Locale.ROOT);
        if ("steve".equals(clean) || "alex".equals(clean)) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/" + ("alex".equals(clean) ? "slim/alex.png" : "wide/steve.png"));
        }

        return CACHE.computeIfAbsent(clean, SkinTextureManager::loadSkin);
    }

    public static ResourceLocation resolveUrlTexture(String url) {
        ResourceLocation cached = CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        if (URL_REQUESTS.putIfAbsent(url, Boolean.TRUE) == null) {
            try {
                downloadSkin(url);
            } catch (IllegalArgumentException exception) {
                CraftersNpc.LOGGER.warn("URL de skin inválida recibida del servidor: {}", url);
            }
        }
        return DEFAULT_STEVE;
    }

    private static void downloadSkin(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "CraftersNpc/1.0")
                .GET()
                .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    byte[] bytes;
                    try (InputStream body = response.body()) {
                        bytes = body.readNBytes(MAX_DOWNLOAD_BYTES + 1);
                    } catch (IOException exception) {
                        CraftersNpc.LOGGER.warn("No se pudo leer la skin URL {}", url, exception);
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || bytes.length == 0 || bytes.length > MAX_DOWNLOAD_BYTES) {
                        CraftersNpc.LOGGER.warn("Skin URL rechazada (HTTP {}, {} bytes): {}", response.statusCode(), bytes.length, url);
                        return;
                    }
                    try (NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                        if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                            CraftersNpc.LOGGER.warn("Skin URL con dimensiones inválidas {}x{}: {}", image.getWidth(), image.getHeight(), url);
                            return;
                        }
                        NativeImage textureImage = NativeImage.read(new ByteArrayInputStream(bytes));
                        Minecraft.getInstance().execute(() -> {
                            try {
                                DynamicTexture texture = new DynamicTexture(textureImage);
                                CACHE.put(url, Minecraft.getInstance().getTextureManager().register("cnpc_url", texture));
                            } catch (RuntimeException exception) {
                                textureImage.close();
                                CraftersNpc.LOGGER.warn("No se pudo registrar la skin URL {}", url, exception);
                            }
                        });
                    } catch (IOException exception) {
                        CraftersNpc.LOGGER.warn("La URL no contiene una skin PNG válida: {}", url, exception);
                    }
                })
                .exceptionally(exception -> {
                    CraftersNpc.LOGGER.warn("No se pudo descargar la skin URL {}", url, exception);
                    return null;
                });
    }

    private static ResourceLocation loadSkin(String skinId) {
        Path path;
        try {
            path = SkinDirectory.ensureFolder().resolve(skinId + ".png");
        } catch (IOException e) {
            CraftersNpc.LOGGER.warn("No se pudo preparar carpeta de skins", e);
            return DEFAULT_STEVE;
        }
        if (!Files.exists(path)) {
            return DEFAULT_STEVE;
        }

        try (InputStream input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);
            DynamicTexture dynamic = new DynamicTexture(image);
            return Minecraft.getInstance().getTextureManager().register("cnpc_" + skinId, dynamic);
        } catch (IOException e) {
            CraftersNpc.LOGGER.warn("No se pudo cargar skin {} desde {}", skinId, path, e);
            return DEFAULT_STEVE;
        }
    }

    public static Path ensureFolder() throws IOException {
        return SkinDirectory.ensureFolder();
    }
}
