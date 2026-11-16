from pathlib import Path
root = Path('src/client/java/com/kuangsanfeng/ultimategarden/client')
source = (root/'function/mapping/MappingController.java').read_text(encoding='utf-8')
assert 'ChunkStatus.FULL,false' in source and 'instanceof EmptyLevelChunk' in source
assert 'ClientChunkEvents.CHUNK_LOAD' in source and 'ClientChunkEvents.CHUNK_UNLOAD' in source
assert 'WorldInfoService.snapshot().island()' in source
assert 'pending.remove(key,revision)' in source  # An old redraw callback cannot remove its replacement task.
assert 'revisions.merge(key,1L,Long::sum)' in source
for name in ('instance()', 'server()', 'mode()', 'subArea()', '.send(', 'sendCommand', 'setViewDistance', 'getChunkAt'):
    assert name not in source, name
for file in (root/'function/mapping').glob('*.java'):
    text = file.read_text(encoding='utf-8')
    for forbidden in ('net.minecraftforge', 'net.neoforged', '/locraw', 'sendCommand(', '.send('):
        assert forbidden not in text, (file, forbidden)
hotkey = (root/'runtime/hotkey/MacroKeyBindings.java').read_text(encoding='utf-8')
for key in ('mapping_initialize', 'mapping_toggle', 'mapping_redraw'):
    assert f'register("{key}", GLFW.GLFW_KEY_UNKNOWN)' in hotkey
menu = (root/'platform/ui/adapters/menu/GardenMenuAdapter.java').read_text(encoding='utf-8')
assert 'mappingSettings(developer)' in menu and 'MappingConfirmButton' in menu
print('Mapping wiring: loaded-only query, passive region identity, no network calls, three unbound hotkeys, developer menu passed')

replay = (root/'platform/ui/screen/MappingReplayScreen.java').read_text(encoding='utf-8')
hud = (root/'platform/ui/adapters/mapping/MappingHud.java').read_text(encoding='utf-8')
assert 'recordingSnapshot()' in hud and 'Files.' not in hud and 'NbtIo' not in hud
assert replay.count('recordingSnapshot') == 0  # Preview selections never mutate the recorder context.
for file in (root/'function/mapping/replay').glob('*.java'):
    code = file.read_text(encoding='utf-8')
    for forbidden in ('ClientLevel', 'HypixelModAPI', 'sendCommand', '.send(', 'subscribeToEventPacket'):
        assert forbidden not in code, (file,forbidden)
assert 'layer' in replay and 'editLocalMap' in replay
assert menu.index('mapping.delete_chunk') < menu.index('mapping.reset')
assert 'mapping.current_status' in menu and 'mapping.open_replay' in menu
print('Replay wiring: offline-only data, snapshot-only HUD, current/replay menu hierarchy and delete order passed')

# One instance-local path authority, used by recorder, replay and replay management.
assert source.count('MappingPaths.root(') == 2 and 'MappingPaths.root(' in replay
assert 'config/map' not in source and 'config/map' not in replay
assert replay.index('new Label("distance"') < replay.index('new Label("chunk_borders"') < replay.index('new Label("blur"')
backdrop=(root/'platform/ui/adapters/mapping/ReplayBackdrop.java').read_text(encoding='utf-8')
hook=(root/'mixin/MappingReplayBackgroundMixin.java').read_text(encoding='utf-8')
assert 'Screenshot' not in backdrop and 'Executor' not in backdrop and 'processWorld()' in backdrop
assert 'GuiRenderer;render(' in hook and 'processWorldBackground()' in hook
assert 'isPauseScreen(){return false;}' in replay
assert 'ToggleSwitch' in replay and 'dirty=true;' in replay
print('Replay update wiring: shared new map root, distance/borders/blur order, same-frame GPU before GUI, no frozen screenshot worker passed')
