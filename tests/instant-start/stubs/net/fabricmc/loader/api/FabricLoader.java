package net.fabricmc.loader.api;
import java.nio.file.Path;public final class FabricLoader {public static FabricLoader getInstance(){return new FabricLoader();}public Path getGameDir(){return Path.of("build/instant-start-audit/game");}public Path getConfigDir(){return Path.of("build/instant-start-audit/config");}}
