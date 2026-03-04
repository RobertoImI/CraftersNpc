package org.crafterscr.craftersnpc;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SkinTextureManager {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Supplier<Path> SKINS_DIR = Suppliers.memoize(() -> Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("craftersnpc").resolve("skins"));
    private static final ResourceLocation DEFAULT_STEVE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private SkinTextureManager() {
    }

    public static ResourceLocation resolveTexture(String skinId) {
        String clean = skinId == null || skinId.isBlank() ? "steve" : skinId.toLowerCase();
        if ("steve".equals(clean) || "alex".equals(clean)) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/" + ("alex".equals(clean) ? "slim/alex.png" : "wide/steve.png"));
        }

        return CACHE.computeIfAbsent(clean, SkinTextureManager::loadSkin);
    }

    private static ResourceLocation loadSkin(String skinId) {
        Path path = SKINS_DIR.get().resolve(skinId + ".png");
        if (!Files.exists(path)) {
            return DEFAULT_STEVE;
        }

        try {
            NativeImage image = NativeImage.read(Files.newInputStream(path));
            DynamicTexture dynamic = new DynamicTexture(image);
            return Minecraft.getInstance().getTextureManager().register("cnpc_" + skinId, dynamic);
        } catch (IOException e) {
            CraftersNpc.LOGGER.warn("No se pudo cargar skin {} desde {}", skinId, path, e);
            return DEFAULT_STEVE;
        }
    }

    public static Path ensureFolder() throws IOException {
        Path dir = SKINS_DIR.get();
        Files.createDirectories(dir);
        Path readme = dir.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, "Coloca aquí skins PNG de 64x64. Usa /cnpc skin <nombre_archivo_sin_png> mirando el NPC.\n");
        }
        return dir;
    }
}
