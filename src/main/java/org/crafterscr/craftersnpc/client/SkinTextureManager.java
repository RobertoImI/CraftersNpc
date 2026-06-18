package org.crafterscr.craftersnpc.client;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.skin.SkinDirectory;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinTextureManager {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
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
