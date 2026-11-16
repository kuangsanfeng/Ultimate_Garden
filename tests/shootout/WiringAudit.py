"""Static integration evidence; not a substitute for in-game Mixin validation."""
import json,re,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
b=root/'src/client/java/com/kuangsanfeng/ultimategarden/client'
checks=0
def check(value,label):
 global checks
 checks+=1
 assert value,label
for relative in ('macro/zombie_shoot/aim/ShootoutAimClock.java', 'macro/zombie_shoot/aim/ShootoutAimCompletion.java', 'macro/zombie_shoot/aim/ShootoutAimCorrection.java', 'platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java', 'macro/zombie_shoot/shot/ShootoutArrowConfirmation.java', 'macro/zombie_shoot/ShootoutAutoStart.java', 'macro/zombie_shoot/model/ShootoutBabyStall.java', 'macro/zombie_shoot/targeting/ShootoutCandidateSelection.java', 'macro/zombie_shoot/shot/ShootoutLampCollision.java', 'macro/zombie_shoot/model/ShootoutModel.java', 'macro/zombie_shoot/aim/ShootoutMouseInput.java', 'macro/zombie_shoot/targeting/ShootoutPolicy.java', 'platform/network/ShootoutRtt.java', 'macro/zombie_shoot/shot/ShootoutShotClock.java', 'macro/zombie_shoot/model/ShootoutSpeed.java', 'macro/zombie_shoot/aim/ShootoutTanhAim.java', 'macro/zombie_shoot/targeting/ShootoutTargetLoss.java', 'macro/zombie_shoot/targeting/ShootoutTargetRound.java', 'function/collection/zombie_shoot/ZombieShootArrowAngles.java', 'function/collection/zombie_shoot/ZombieShootBlockCollector.java', 'function/collection/zombie_shoot/ZombieShootClassifier.java', 'function/collection/zombie_shoot/compat/ZombieShootCollector.java', 'function/collection/zombie_shoot/ZombieShootCoordinateSystem.java', 'function/observation/entity/ZombieShootEntityTracker.java', 'function/collection/zombie_shoot/compat/ZombieShootLogGate.java', 'platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java', 'function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java', 'macro/zombie_shoot/ZombieShootoutController.java', 'function/collection/zombie_shoot/ZombieShootPathAnalyzer.java', 'function/collection/zombie_shoot/ZombieShootPathCollector.java', 'platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java', 'platform/persistence/log/zombie_shoot/compat/ZombieShootSnapshot.java'):
 p=b/relative
 s=p.read_text(encoding='utf-8')
 check(not re.search(r'\.(?:setYRot|setXRot|setPos|sendCommand|sendPacket|startMacro|pauseMacro|resetMacro|releaseFocus|restoreFocus)\s*\(',s),'Forbidden direct operation: '+p.name)
controller=(b/'macro/zombie_shoot/ZombieShootoutController.java').read_text(encoding='utf-8')
for marker in ['measureRtt()','>900','distanceToSqr(origin)>1','WORLD_OR_ACCOUNT_CHANGED','WINDOW_UNFOCUSED','FARM_BUSY','MODEL_SCENE_NOT_MATCHED','CONFIG_RESTART_REQUIRED','LOCAL_GEOMETRY_UNKNOWN','if(ping<0)','mouse.cancel','target.round.dispatched()','t.round.verify(shot.roundAttempt']:
 check(marker in controller,'Lifecycle/protection: '+marker)
round_state=(b/'macro/zombie_shoot/targeting/ShootoutTargetRound.java').read_text(encoding='utf-8')
check('ShootoutPolicy.retryAllowed(attempt.retry(),retried,sameIdentity,observedAlive)' in round_state and 'retry=true;retried=true' in round_state,'Single retry within the matching round')
check('!pending.equals(attempt)' in round_state,'Stale verification excluded')
check('ClientTickEvents.END_CLIENT_TICK' in controller and 'ClientContextEvents.onDisconnect' in controller,'Independent lifecycle')
context=(b/'platform/context/ClientContextEvents.java').read_text(encoding='utf-8')
check(context.count('ClientPlayConnectionEvents.DISCONNECT.register(')==1,'One shared disconnect bridge')
check(context.count('ClientLifecycleEvents.CLIENT_STOPPING.register(')==1,'One shared stopping bridge')
check('if(!running)return;' in controller,'No post-stop tick action')
check('analysis' not in controller and 'ActionScheduler' not in controller,'No delayed firing callbacks')
keys=(b/'runtime/hotkey/MacroKeyBindings.java').read_text(encoding='utf-8')
check('register("toggle_zombie_shootout", GLFW.GLFW_KEY_UNKNOWN)' in keys,'Default unbound')
check('SHOOTOUT_EDGE.rising(isPressed(SHOOTOUT_KEY))' in keys,'Rebindable rising edge')
check('shootoutToggle && hudInputAllowed' in keys,'Same GUI/text guard')
defaults=dict(re.findall(r'register\(\s*"([^"]+)"\s*,\s*GLFW\.(GLFW_KEY_\w+)\s*\)',keys))
for name,key in {'macro_toggle':'KP_ADD','macro_reset':'KP_SUBTRACT','next_line':'RIGHT_BRACKET','previous_line':'LEFT_BRACKET','spawn':'KP_MULTIPLY','warp':'KP_DIVIDE','pest_loadout':'KP_7','pest_timer_cancel':'KP_4','loadout':'KP_9','crop_change':'KP_DECIMAL','window_focus':'U','open_menu':'M','toggle_hud':'UNKNOWN'}.items():check(defaults.get(name)=='GLFW_KEY_'+key,'Existing default: '+name)
mouse=(b/'platform/input/view/NativeViewInput.java').read_text(encoding='utf-8')
check('m.shootout$turn(dt)' in mouse and 'source' in mouse.lower(),'Native mouse consumer')
check('new MouseButtonInfo(1,0),1' in mouse and 'new MouseButtonInfo(1,0),0' in mouse,'Complete native right click')
check('if(clickPending)' in mouse and 'if(rightOwned)' in mouse,'Source-owned cancellation')
screen=(b/'platform/ui/adapters/menu/GardenMenuAdapter.java').read_text(encoding='utf-8')
check('controller::enabled,controller::toggle' in screen,'One UI/controller state source')
check('nestedDelaySection("ui.ultimategarden.shootout.title",parent)' in screen,'Reuse nested menu')
def unique(pairs):
 d={}
 for key,value in pairs:
  if key in d:raise AssertionError('Duplicate language key: '+key)
  d[key]=value
 return d
langs=root/'src/main/resources/assets/ultimate_garden/lang'
en=json.loads((langs/'en_us.json').read_text(encoding='utf-8'),object_pairs_hook=unique)
zh=json.loads((langs/'zh_cn.json').read_text(encoding='utf-8'),object_pairs_hook=unique)
check(en.keys()==zh.keys(),'Language key parity')
for d in (en,zh):check(d['key.ultimate_garden.toggle_zombie_shootout']==d['ui.ultimategarden.shootout.hotkey'],'Native/menu binding names')
check(en['key.ultimate_garden.toggle_zombie_shootout']=='Toggle Zombie Shootout','English fallback')
click_start=controller.index('  if(mouse.click(c))')
click=controller[click_start:controller.index(' }catch(Exception e)',click_start)]
check('handled=true' not in click and 'shots.add' not in click and 'current=select()' not in click,'Click cannot complete a shot or switch target')
check('arrowConfirmation.attempted' in click and 'shotClock.fired' in click,'Each submitted attempt retains cooldown')
check(controller.count('shots.add(new Shot')==1,'Single confirmed-shot record creation path')
finish=controller[controller.index(' private void finishConfirmedShot()'):controller.index(' private boolean confirmationExpired')]
check('confirmed==null' in finish and 'confirmed.arrow().uuid()' in finish and 'confirmed.confirmationTick()' in finish,'Confirmed UUID/time gate record creation')
check(controller.count('aimReady(c,')>=3 and 'config().get(AIM_TOLERANCE)' in controller,'Actual view tolerance rechecked for both predictions')
check(controller.index('if(confirmationExpired(arrowNow))')<controller.index('if(c.screen!=null||c.getOverlay()!=null)'), 'Timeout cannot freeze behind a GUI')
check('arrowConfirmation.cancel();try{' in controller,'Stop cancels pending arrow cycle before cleanup')
check('Setting.AIM_TOLERANCE' in screen and 'Setting.AIM_CURVATURE' in screen and 'String unit)' in screen,'Degree unit uses existing numeric input')
listener=(b/'runtime/command/CommandListener.java').read_text(encoding='utf-8')
check(listener.count('ClientReceiveMessageEvents.GAME.register(')==1,'Reuse single server message callback')
check('controller.automateTaskByMonitorMessage(text);' in listener and 'if (!overlay)' in listener,'Preserve Pest dispatch and exclude overlay')
check('tickAutoStart();if(!running)return;' in controller,'Auto start tick runs while stopped')
check('public void start(){cancelAutoStart();' in controller and 'cancelAutoStart();state=State.STOPPING'  in controller,'Manual and safety lifecycle cancel pending')
check('setAutoStartCancellation(this::cancelAutoStart)' in controller,'Config OFF cancels immediately')
jar=root/'build/libs/ultimate_garden-1.0.2+26.1.2.jar'
with zipfile.ZipFile(jar) as z:
 check(z.read('ultimategarden/zombie_shoot_theoretical_models.json')==(root/'src/client/resources/ultimategarden/zombie_shoot_theoretical_models.json').read_bytes(),'Exact supplied model packaged')
 mixins=json.loads(z.read('ultimate_garden.client.mixins.json'))
 check('ShootoutMouseAccessor' in mixins['client'],'Input accessor registered')
print(f'PASS: {checks} static lifecycle, source isolation, binding defaults, menu linkage, language and packaged-model checks; Not Verified In-Game.')
