"""Check the installed Minecraft/Fabric source order, not a reconstructed design."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[2]
jar = next((root / '.gradle/loom-cache/minecraftMaven').rglob('minecraft-clientOnly-*-sources.jar'))
checks = []
with zipfile.ZipFile(jar) as archive:
    minecraft = archive.read('net/minecraft/client/Minecraft.java').decode('utf-8')
    keyboard = archive.read('net/minecraft/client/KeyboardHandler.java').decode('utf-8')
    mapping = archive.read('net/minecraft/client/KeyMapping.java').decode('utf-8')

def ordered(label, source, *fragments):
    cursor = 0
    lines = []
    for fragment in fragments:
        position = source.index(fragment, cursor)
        lines.append(source.count('\n', 0, position) + 1)
        cursor = position + len(fragment)
    checks.append(f'{label}: source lines {lines}')

ordered('KeyboardHandler physical click', keyboard, 'private void keyPress(',
        'KeyMapping.set(key, true);', 'KeyMapping.click(key);')
ordered('Minecraft inventory consumption and creation', minecraft, 'private void handleKeybinds()',
        'while (this.options.keyInventory.consumeClick())', 'this.player.sendOpenInventory();',
        'this.setScreen(new InventoryScreen(this.player));')
ordered('Minecraft setScreen before init', minecraft, 'public void setScreen(',
        'this.screen = screen;', 'KeyMapping.releaseAll();', 'screen.init(')
ordered('Minecraft tick handles keys before keyboard tail', minecraft, 'public void tick()',
        'this.handleKeybinds();', 'this.keyboardHandler.tick();')
ordered('KeyMapping click queue', mapping, 'public static void click(', 'keyMapping.clickCount++')
ordered('KeyMapping consume and release', mapping, 'public boolean consumeClick()',
        'this.clickCount--;', 'protected void release()', 'this.clickCount = 0;')

cache = Path.home() / '.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api'
fabric = next(cache.rglob('fabric-lifecycle-events-v1-*-sources.jar'))
with zipfile.ZipFile(fabric) as archive:
    source = archive.read('net/fabricmc/fabric/mixin/event/lifecycle/client/MinecraftMixin.java').decode('utf-8')
ordered('Fabric end tick injection', source,
        '@Inject(at = @At("RETURN"), method = "tick")',
        'ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick')

out = root / 'build/gui-audit/inventory-event-order.txt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('\n'.join([
    f'Minecraft source jar: {jar}',
    f'SHA256: {hashlib.sha256(jar.read_bytes()).hexdigest()}',
    f'Fabric source jar: {fabric}', *checks,
    'Scope: actual cached source order verified; no live game input was performed.',
]), encoding='utf-8')
print(f'PASS: {len(checks)} installed Minecraft/Fabric event-order checks. Evidence: {out}')
