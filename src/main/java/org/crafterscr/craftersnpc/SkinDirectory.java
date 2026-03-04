package org.crafterscr.craftersnpc;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SkinDirectory {
    private static final Path SKINS_DIR = FMLPaths.GAMEDIR.get().resolve("config").resolve("craftersnpc").resolve("skins");

    private SkinDirectory() {
    }

    public static Path ensureFolder() throws IOException {
        Files.createDirectories(SKINS_DIR);
        Path readme = SKINS_DIR.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, "Coloca aquí skins PNG de 64x64.\n");
        }
        return SKINS_DIR;
    }

    public static List<String> listSkins() {
        try {
            ensureFolder();
            try (Stream<Path> stream = Files.list(SKINS_DIR)) {
                return stream
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(path -> path.getFileName().toString())
                        .map(name -> name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            CraftersNpc.LOGGER.warn("No se pudo listar skins", e);
            return List.of();
        }
    }
}
