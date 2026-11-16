
import com.kuangsanfeng.ultimategarden.client.runtime.world.esp.adapters.PestESP.Detection;
import java.util.*;

public final class PestDetectionRegression {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("Pest check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        String[] bats={"Fly","Mosquito","Dragonfly","Firefly","Praying Mantis","Lunar Moth"};
        String[] fish={"Cricket","Locust","Rat","Earthworm","Mite","Moth","Slug","Beetle","Field Mouse"};
        for(String[] names:List.of(bats,fish)) for(String name:names) {
            check(name.equals(Detection.parse("§cൠ §a"+name+" §e12,345/99,999§c❤")));
            check(Detection.matchesType(name,names==bats)); check(!Detection.matchesType(name,names!=bats));
            check(name.equals(Detection.parse("[Lv1] "+name+" 1.5k HP")));
        }
        for(String invalid:List.of("Moon Moth 600❤","Lunar Mothling 600❤","Not a Slug","Slug 600 garbage","Moth rat","Endermite 600❤")) check(Detection.parse(invalid)==null);
        check(Detection.parse("Lunar Moth").equals("Lunar Moth"));
        var a=new Detection.Body(false,0,1,0); var b=new Detection.Body(true,5,1,0);
        var slug=new Detection.Tag("Slug",0,2,0); var mosquito=new Detection.Tag("Mosquito",5,2,0);
        check(Detection.associate(List.of(a,b),List.of(slug,mosquito)).equals(List.of(0,1)));
        check(Detection.associate(List.of(a),List.of(new Detection.Tag("Mosquito",0,2,0))).isEmpty());
        check(Detection.associate(List.of(a,a),List.of(slug)).isEmpty());
        check(Detection.associate(List.of(a),List.of(slug,slug)).isEmpty());
        check(Detection.associate(List.of(a),List.of(new Detection.Tag("Slug",2,2,0))).isEmpty());
        check(Detection.associate(List.of(a),List.of(new Detection.Tag("Slug",0,5,0))).isEmpty());
        check(Detection.associate(List.of(a),List.of()).isEmpty());
        var directory=java.nio.file.Files.createTempDirectory("ug-pest-detection-");
        var configs=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager(directory);
        check(configs.getPestConfig().isPestESPPreciseDetection());
        configs.getPestConfig().setPestESPPreciseDetection(false);
        check(configs.save(com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager.Module.PEST));
        var loaded=new com.kuangsanfeng.ultimategarden.client.platform.persistence.config.ConfigManager(directory);
        check(!loaded.getPestConfig().isPestESPPreciseDetection());
        check(loaded.getPestConfig().isAutoPestLoadout() && loaded.getPestConfig().getPestLoadoutDelayTicks()==2500);
        System.out.println("PASS: "+checks+" pest parser/type/association/persistence checks; server geometry remains unverified.");
    }
}
