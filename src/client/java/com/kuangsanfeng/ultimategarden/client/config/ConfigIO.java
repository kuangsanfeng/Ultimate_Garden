package com.kuangsanfeng.ultimategarden.client.config;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.kuangsanfeng.ultimategarden.Ultimate_Garden;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** JSON disk operations only. Callers own schemas and effective defaults. */
public final class ConfigIO {
    @FunctionalInterface public interface PathCheck { void check() throws IOException; }
    public record CopyResult(boolean success, List<String> rollbackFailures) { }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public JsonObject read(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new JsonParseException("Expected a single JSON object");
            return value.getAsJsonObject();
        }
    }

    public boolean backup(Path file) {
        return backup(file, "broken");
    }
    public boolean backupForMigration(Path file) {
        return backup(file, "migrated");
    }
    private boolean backup(Path file, String reason) {
        try {
            Path backup = file.resolveSibling(file.getFileName() + "." + reason + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID());
            Files.copy(file, backup);
            Ultimate_Garden.LOGGER.info("Backed up config {} to {}", file, backup);
            return true;
        } catch (IOException | RuntimeException e) {
            Ultimate_Garden.LOGGER.error("Cannot back up config {}; original will not be overwritten", file, e);
            return false;
        }
    }

    public boolean write(Path file, JsonObject data) {
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Ultimate_Garden.LOGGER.error("Cannot save config {}", file, e);
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); }
            catch (IOException e) { Ultimate_Garden.LOGGER.warn("Cannot remove temporary config {}", temp, e); }
        }
    }

    /** Stage and back up every selected file before replacing any destination. */
    public CopyResult replaceTransaction(Map<Path,JsonObject> documents, PathCheck check) {
        String transaction=System.currentTimeMillis()+"-"+UUID.randomUUID();
        Map<Path,Path> stages=new LinkedHashMap<>(), backups=new LinkedHashMap<>();
        List<Path> replaced=new ArrayList<>(); List<String> rollbackFailures=new ArrayList<>();
        try {
            for(var entry:documents.entrySet()) {
                check.check(); Path target=entry.getKey();
                Path stage=Files.createTempFile(target.getParent(),".ug-copy-",".tmp"); stages.put(target,stage);
                if(!write(stage,entry.getValue())) throw new IOException("Cannot stage selected configuration");
                check.check();
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                    AccountDirectories.regular(target);
                    Path backup=target.resolveSibling(target.getFileName()+".overwrite-"+transaction);
                    Files.copy(target,backup); backups.put(target,backup);
                }
            }
            for(var entry:stages.entrySet()) {
                check.check(); move(entry.getValue(),entry.getKey()); replaced.add(entry.getKey());
            }
            return new CopyResult(true,List.of());
        } catch(IOException | RuntimeException error) {
            Ultimate_Garden.LOGGER.error("Configuration copy failed; restoring selected files",error);
            Collections.reverse(replaced);
            for(Path target:replaced) {
                try {
                    check.check(); Path backup=backups.get(target);
                    if(backup==null) Files.deleteIfExists(target);
                    else {
                        Path restore=Files.createTempFile(target.getParent(),".ug-rollback-",".tmp");
                        try { Files.copy(backup,restore,StandardCopyOption.REPLACE_EXISTING); check.check(); move(restore,target); }
                        finally { Files.deleteIfExists(restore); }
                    }
                } catch(IOException | RuntimeException rollback) {
                    rollbackFailures.add(target.getFileName().toString());
                    Ultimate_Garden.LOGGER.error("Could not restore {}; overwrite backup retained",target,rollback);
                }
            }
            return new CopyResult(false,List.copyOf(rollbackFailures));
        } finally {
            for(Path stage:stages.values()) try { check.check(); Files.deleteIfExists(stage); }
            catch(IOException | RuntimeException error) { Ultimate_Garden.LOGGER.warn("Staged configuration cleanup failed",error); }
        }
    }
    private static void move(Path from,Path to) throws IOException {
        try { Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException e) { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING); }
    }
}
