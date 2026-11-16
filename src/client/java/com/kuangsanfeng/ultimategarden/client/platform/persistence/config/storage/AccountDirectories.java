package com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.AccountIdentity;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.storage.ConfigIO;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager;


import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.nio.channels.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Validates named account folders against UUID metadata and the canonical root. */
public final class AccountDirectories {
    public record Profile(UUID uuid, String name) { public boolean legacy() { return uuid == null; } }
    public static final Profile LEGACY = new Profile(null, "legacy");
    private final Path root;
    private final ConfigIO io;
    private static final Map<Path,StoreLock> LOCKS=new ConcurrentHashMap<>();
    private static final class StoreLock { final ReentrantLock mutex=new ReentrantLock(); FileChannel channel; FileLock file; }
    public interface Guard extends AutoCloseable { @Override void close() throws IOException; }
    public AccountDirectories(Path root, ConfigIO io) { this.root = root.toAbsolutePath().normalize(); this.io = io; }
    private void ensureRoot() throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("Linked configuration root");
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid configuration root");
        if(!root.toRealPath().equals(root.getParent().toRealPath().resolve(root.getFileName()))) throw new IOException("Redirected configuration root");
    }
    public Guard lock() throws IOException {
        ensureRoot(); StoreLock store=LOCKS.computeIfAbsent(root.toRealPath(),ignored->new StoreLock()); store.mutex.lock();
        try {
            if(store.mutex.getHoldCount()==1) {
                Path path=root.resolve(".account-storage.lock");
                if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) regular(path);
                store.channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                store.file=store.channel.lock();
            }
        } catch(IOException | RuntimeException error) {
            try { if(store.mutex.getHoldCount()==1 && store.channel!=null) store.channel.close(); }
            finally { store.mutex.unlock(); }
            throw error;
        }
        return () -> {
            try { if(store.mutex.getHoldCount()==1) { try { store.file.release(); } finally { store.channel.close(); } } }
            finally { store.mutex.unlock(); }
        };
    }
    private Path named(String name) throws IOException {
        new AccountIdentity(new UUID(0,1), name);
        ensureRoot(); Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root)) throw new IOException("Account path outside root");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().equals(root.toRealPath().resolve(path.getFileName())))) throw new IOException("Linked or invalid account directory");
        return path;
    }
    private Profile read(Path folder) throws IOException {
        Path path = named(folder.getFileName().toString()); Path metadata = path.resolve("account.json");
        regular(metadata);
        try {
            JsonObject json = io.read(metadata);
            if (!json.get("schemaVersion").isJsonPrimitive() || !json.getAsJsonPrimitive("schemaVersion").isNumber()
                    || json.get("schemaVersion").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Unknown identity schema");
            if(!json.get("uuid").isJsonPrimitive() || !json.getAsJsonPrimitive("uuid").isString()
                    || !json.get("name").isJsonPrimitive() || !json.getAsJsonPrimitive("name").isString()) throw new IllegalArgumentException("Invalid identity fields");
            AccountIdentity id = new AccountIdentity(UUID.fromString(json.get("uuid").getAsString()), json.get("name").getAsString());
            if (!id.name().equalsIgnoreCase(path.getFileName().toString())) throw new IllegalArgumentException("Identity name mismatch");
            return new Profile(id.uuid(), path.getFileName().toString());
        } catch (RuntimeException e) { throw new IOException("Unverified account directory", e); }
    }
    public List<Profile> list() throws IOException {
        ensureRoot(); List<Profile> found = new ArrayList<>();
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                try { found.add(read(path)); } catch (IOException | IllegalArgumentException ignored) { /* Never claim unverified folders. */ }
            }
        }
        Map<UUID, Long> counts = new HashMap<>(); for (Profile profile : found) counts.merge(profile.uuid(),1L,Long::sum);
        return found.stream().filter(p -> counts.get(p.uuid()) == 1).sorted(Comparator.comparing(Profile::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public Profile resolve(AccountIdentity identity) throws IOException {
        ensureRoot(); Path destination = named(identity.name());
        List<Profile> matching = new ArrayList<>();
        try(var paths=Files.list(root)) {
            for(Path path:paths.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList()) {
                try { Profile p=read(path); if(p.uuid().equals(identity.uuid())) matching.add(p); } catch(IOException | IllegalArgumentException ignored) { }
            }
        }
        if(matching.size()>1) throw new IOException("Multiple directories claim this UUID");
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {
            Profile existing=read(destination);
            if(!existing.uuid().equals(identity.uuid()) || (!matching.isEmpty() && !named(matching.getFirst().name()).equals(destination)))
                throw new IOException("Account name is owned by a different identity");
            return existing;
        }
        if(!matching.isEmpty()) {
            Path previous=verified(matching.getFirst());
            Files.move(previous,destination);
        } else Files.createDirectory(destination);
        JsonObject metadata=new JsonObject(); metadata.addProperty("schemaVersion",1);
        metadata.addProperty("uuid",identity.uuid().toString()); metadata.addProperty("name",identity.name());
        if(!io.write(destination.resolve("account.json"),metadata)) throw new IOException("Cannot save account identity");
        return read(destination);
    }
    public Path verified(Profile profile) throws IOException {
        if(profile.legacy()) { if(!profile.equals(LEGACY)) throw new IOException("Invalid legacy profile"); ensureRoot(); return root; }
        Path path=named(profile.name()); Profile actual=read(path);
        if(!actual.uuid().equals(profile.uuid())) throw new IOException("Account identity changed");
        if(!list().contains(actual)) throw new IOException("Ambiguous account identity");
        return path;
    }
    public Path file(Profile profile, ConfigManager.Module module) throws IOException {
        Path file=verified(profile).resolve(module.name().toLowerCase(Locale.ROOT)+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) regular(file);
        return file;
    }
    public boolean hasLegacy() throws IOException {
        ensureRoot();
        for(var module:ConfigManager.Module.values()) if(Files.isRegularFile(root.resolve(module.name().toLowerCase(Locale.ROOT)+".json"),LinkOption.NOFOLLOW_LINKS)) return true;
        return false;
    }
    public static void regular(Path file) throws IOException {
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing, linked or non-regular configuration file");
    }
}
