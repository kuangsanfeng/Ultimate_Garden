package com.kuangsanfeng.ultimategarden.client.function.path_finding;

import com.google.gson.JsonObject;
import com.kuangsanfeng.ultimategarden.client.platform.persistence.config.model.shootout.ZombieShootoutConfig;
import static com.kuangsanfeng.ultimategarden.client.function.path_finding.PathTypes.*;

/** Account-local settings. Observed movement capabilities belong to the client session. */
public final class PathSettings {
    public Mode mode = Mode.NORMAL;
    public boolean transmission = true, speedDetection = true, initialSpeed = true, jumpDetection = true, initialJump = true;
    private int teleportCooldownMs = 20;
    private boolean gpuAcceleration = true;
    private int cpuWorkers=legacyWorkerSelection();
    private static int legacyWorkerSelection(){return Math.max(0,Integer.getInteger("ug.path.workers",0));}
    public static int availableWorkers(){return Runtime.getRuntime().availableProcessors();}
    public boolean gpuAcceleration(){return gpuAcceleration;}
    public void setGpuAcceleration(boolean enabled){gpuAcceleration=enabled;}
    public int cpuWorkers(){return cpuWorkers;}
    public int effectiveWorkers(){return cpuWorkers==0?availableWorkers():Math.min(cpuWorkers,availableWorkers());}
    public void setCpuWorkers(int workers){
        if(workers<0||workers>availableWorkers())throw new IllegalArgumentException("Invalid CPU worker count");
        cpuWorkers=workers;
    }
    private int maximumSearchSegments = DEFAULT_SEARCH_ROUNDS;
    private long searchNodesPerSegment = DEFAULT_NODES_PER_ROUND;
    private int coordinateWindowMs = 5000;
    public int maximumSearchSegments() { return maximumSearchSegments; }
    public long searchNodesPerSegment() { return searchNodesPerSegment; }
    public int coordinateWindowMs() { return coordinateWindowMs; }
    public void setBudget(long nodes, int segments) {
        if (nodes<1 || segments<1) throw new IllegalArgumentException("Invalid search budget");
        Math.multiplyExact(nodes,segments);
        searchNodesPerSegment=nodes;
        maximumSearchSegments=segments;
    }
    public void setCoordinateWindowMs(int value) {
        if (value<1) throw new IllegalArgumentException("Invalid coordinate window");
        coordinateWindowMs=value;
    }

    public enum Aim {
        SPEED(ZombieShootoutConfig.Setting.AIM_SPEED),
        CURVE(ZombieShootoutConfig.Setting.AIM_CURVATURE),
        RATE(ZombieShootoutConfig.Setting.AIM_UPDATE_RATE),
        TOLERANCE(ZombieShootoutConfig.Setting.AIM_TOLERANCE),
        CORRECTION(ZombieShootoutConfig.Setting.AIM_CORRECTION_RATIO);
        private final ZombieShootoutConfig.Setting source;
        Aim(ZombieShootoutConfig.Setting source) {this.source=source;}
        public String field(){return source.field;}
    }
    private final ZombieShootoutConfig aim = new ZombieShootoutConfig();
    public double aim(Aim setting) {return aim.get(setting.source);}
    public void aim(Aim setting,double value) {aim.set(setting.source,value);}
    private double speedSampleDistance = 80;
    public Weights weights = Weights.defaults();

    public int teleportCooldownMs() { return teleportCooldownMs; }
    public double speedSampleDistance() { return speedSampleDistance; }
    public void setCooldown(int value) {
        if (value < 0 || value > 60_000) throw new IllegalArgumentException("Invalid teleport cooldown");
        teleportCooldownMs = value;
    }
    public void setSpeedSampleDistance(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid sample distance");
        speedSampleDistance = value;
    }
    public Options options(double distance,double jumpHeight) {
        return new Options(mode,transmission,true,distance,jumpHeight,searchNodesPerSegment,weights);
    }
    public boolean load(JsonObject document) {
        if (!document.has("pathFinding")) return false;
        try {
            var data=document.getAsJsonObject("pathFinding");
            gpuAcceleration=flag(data,"gpuAcceleration");
            cpuWorkers=data.has("cpuWorkers")?data.get("cpuWorkers").getAsBigDecimal().intValueExact():legacyWorkerSelection();
            if(cpuWorkers<0)throw new IllegalArgumentException("Invalid CPU worker count");
            mode=Mode.valueOf(string(data,"mode",mode.name()));
            transmission=flag(data,"transmission");
            speedDetection=flag(data,"speedDetection");
            initialSpeed=flag(data,"initialSpeed");
            jumpDetection=flag(data,"jumpDetection");
            initialJump=flag(data,"initialJump");
            if (data.has("teleportCooldownMs")) setCooldown(data.get("teleportCooldownMs").getAsBigDecimal().intValueExact());
            setBudget(data.has("searchNodesPerSegment")?data.get("searchNodesPerSegment").getAsBigDecimal().longValueExact():DEFAULT_NODES_PER_ROUND,
                    data.has("maximumSearchSegments")?data.get("maximumSearchSegments").getAsBigDecimal().intValueExact():DEFAULT_SEARCH_ROUNDS);
            if (data.has("coordinateWindowMs")) setCoordinateWindowMs(data.get("coordinateWindowMs").getAsBigDecimal().intValueExact());
            for (var key : Aim.values()) aim(key,number(data,key.field(),aim(key)));
            setSpeedSampleDistance(number(data,"speedSampleDistance",speedSampleDistance));
            var defaults=Weights.defaults();
            weights=new Weights(number(data,"teleportWeight",defaults.transmission()),number(data,"walkWeight",defaults.walk()),
                    number(data,"jumpWeight",defaults.jump()),number(data,"fallWeight",defaults.fall()),
                    number(data,"turnWeight",defaults.turn()),number(data,"turnExponent",defaults.exponent()));
            return false;
        } catch (RuntimeException invalid) { return true; }
    }
    private static String string(JsonObject obj,String key,String fallback) {
        return obj.has(key)?obj.get(key).getAsString():fallback;
    }
    private static boolean flag(JsonObject obj,String key) {
        if (!obj.has(key)) return true;
        if (!obj.get(key).isJsonPrimitive() || !obj.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key);
        return obj.get(key).getAsBoolean();
    }
    private static double number(JsonObject obj,String key,double fallback) {
        if (!obj.has(key)) return fallback;
        if (!obj.get(key).isJsonPrimitive() || !obj.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key);
        return obj.get(key).getAsDouble();
    }
    public JsonObject toJson() {
        var data=new JsonObject();
        data.addProperty("mode",mode.name());
        data.addProperty("gpuAcceleration",gpuAcceleration);
        data.addProperty("cpuWorkers",cpuWorkers);
        data.addProperty("transmission",transmission);
        data.addProperty("teleportCooldownMs",teleportCooldownMs);
        data.addProperty("searchNodesPerSegment",searchNodesPerSegment);
        data.addProperty("maximumSearchSegments",maximumSearchSegments);
        data.addProperty("coordinateWindowMs",coordinateWindowMs);
        for (var key : Aim.values()) data.addProperty(key.field(),aim(key));
        data.addProperty("speedDetection",speedDetection);
        data.addProperty("initialSpeed",initialSpeed);
        data.addProperty("speedSampleDistance",speedSampleDistance);
        data.addProperty("jumpDetection",jumpDetection);
        data.addProperty("initialJump",initialJump);
        data.addProperty("teleportWeight",weights.transmission());
        data.addProperty("walkWeight",weights.walk());
        data.addProperty("jumpWeight",weights.jump());
        data.addProperty("fallWeight",weights.fall());
        data.addProperty("turnWeight",weights.turn());
        data.addProperty("turnExponent",weights.exponent());
        return data;
    }
}
