"""Start a real graphical Minecraft client and require its captured inspection scenes."""
from pathlib import Path
import subprocess
import shutil
import sys
import struct
import time
import os

def annotate_error(message):
    if 'GITHUB_ACTIONS' in os.environ:
        print('::error title=Minecraft graphical validation::'+str(message).replace('%','%25').replace('\n','%0A')[:500])

def fail(message):
    annotate_error(message)
    raise SystemExit(message)

def annotate_failure(error_type, error, tb):
    annotate_error(error)
    sys.__excepthook__(error_type,error,tb)

sys.excepthook=annotate_failure

if not shutil.which('xvfb-run'):
    fail('Xvfb is unavailable.')
source=Path('run-gametest/world')
if not source.exists():
    fail('GameTest world unavailable for graphical inspection.')
# Replace this generated fixture before the first JVM; the second JVM below reopens it unchanged.
visual_world=Path('run/saves/visual-check')
if visual_world.exists():shutil.rmtree(visual_world)
shutil.copytree(source,visual_world)
# Use Portuguese labels and keep vanilla tutorial overlays out of UI captures.
Path('run').mkdir(exist_ok=True)
Path('run/options.txt').write_text('lang:pt_br\nguiScale:3\nsimulationDistance:8\ntutorialStep:none\n')
log=Path('client-console.log')
started=time.time()
with log.open('w') as out:
    result=subprocess.run(['xvfb-run','-a','-s','-screen 0 1920x1080x24','bash','gradlew','--no-daemon','-Pvisual_check=true','runClient'],stdout=out,stderr=subprocess.STDOUT)
text=log.read_text(errors='replace')
print('\n'.join(text.splitlines()[-35:]))
for name in ['visual-chests.png','visual-doors.png','visual-anchor-slots.png','visual-reference-door.png','visual-chest-cavity.png','visual-courier-deposit.png','visual-courier-closed.png','visual-steve-opacity-multiple.png','visual-kraftcity-limits-two.png','visual-kraftcity-limits-removed.png','visual-kraftcity-limits-off.png','visual-industrial-hiring.png','visual-farmer-inventory.png','visual-farmer-long-name.png','visual-worker-inventory.png','visual-worker-dismiss.png','visual-worker-dismissed.png','visual-replaced-workstation.png','visual-commercial-pending.png','visual-rehired-progress.png','visual-residential-employed.png','visual-industry-blue-limits.png','visual-farmer-hoe.png','visual-farmer-field-2.png','visual-farmer-field-3.png','visual-farmer-field-4.png','visual-military-hiring.png','visual-military-soldier.png','visual-military-blacklist.png','visual-military-guard.png','visual-military-zombie.png','visual-builder-hiring.png','visual-builder-global.png','visual-builder-global-fifty.png','visual-builder-capture.png','visual-builder-materials.png','visual-builder-print.png','visual-builder-hologram.png','visual-builder-cooperative.png','visual-builder-flight.png','visual-commerce-hiring.png','visual-commerce-baker.png','visual-commerce-confectioner.png','visual-commerce-grill_cook.png']+[f'visual-industry-{name}.png' for name in ('farmer','butcher','shepherd','fisherman','breeder','beekeeper')]+[f'visual-steve-opacity-{percent}.png' for percent in (0,10,25,50,75,90,100)]:
    path=Path('build/screenshots')/name
    if not path.exists() or path.stat().st_mtime < started:
        errors=[line.strip() for line in text.splitlines() if 'Exception:' in line and any(
            scope in line for scope in ('Farmer visual:', 'IllegalStateException:', 'MixinApplyError', 'AssertionError')
        )]
        if errors:
            annotate_error(errors[-1])
        fail('Minecraft did not capture a fresh '+name)
    data=path.read_bytes()
    if data[:8]!=bytes.fromhex('89504e470d0a1a0a') or struct.unpack('>II',data[16:24])!=(1600,900):
        fail('Invalid Minecraft screenshot: '+name)
if result.returncode and 'Gradle build daemon disappeared unexpectedly' not in text:
    fail(result.returncode)
for marker in ['COURIER_PLAYER_OVERLAP_PASSED','COURIER_CLIENT_OPEN_CLOSE_PASSED','COURIER_GRAPHICAL_DEPOSIT_PASSED','STEVE_WORLD_OPACITY_50_PASSED','STEVE_WORLD_OPACITY_PERCENTAGES_PASSED','STEVE_MULTI_RESIDENT_OPACITY_SYNC_PASSED','STEVE_MULTI_RESIDENT_OPACITY_CAPTURED']:
    if marker not in text:fail('Minecraft client check missing '+marker)
for marker in ['EMPLOYMENT_GRAPHICAL_CONTRACT_PASSED','EMPLOYMENT_INVENTORY_SHARED_PASSED','EMPLOYMENT_DISMISS_GRAPHICAL_PASSED','EMPLOYMENT_FARMER_RECOVERY_DROPS_PASSED','COMMERCIAL_PENDING_NO_CHARGE_PASSED','EMPLOYMENT_REHIRE_PROGRESS_PASSED','EMPLOYMENT_RESIDENTIAL_INDICATOR_PASSED']:
    if marker not in text:fail('Employment client check missing '+marker)
if 'INDUSTRY_BLUE_LIMITS_PASSED' not in text:fail('Blue work area toggle was not validated.')
for marker in ('FARMER_HELD_HOE_RENDERED_AT_DOUBLE_SCALE','FARMER_ZIGZAG_HARVEST_JUMP_DISCARD_AND_SLEEP_PASSED',
               'FARMER_VISITOR_EXIT_AND_BLOCKED_ROUTE_RECOVERY_PASSED','FARMER_THREE_DISTINCT_BEDS_PASSED',
               'FARMER_FLOWING_CHANNEL_EXIT_PASSED','FARMER_MIXED_SOIL_ZIGZAG_PASSED','FARMER_MANUAL_AREA_SELECTION_PASSED','FARMER_FORGE_INVENTORY_PARTIAL_PASSED',
               'FARMER_NIGHT_PURSUIT_HOME_RETURN_PASSED','FARMER_THREE_DISCARDS_AND_SHARED_FORGE_PASSED','FARMER_AUTHORIZED_ENDER_DISCARD_PASSED'):
    if marker not in text:fail('Farmer client check missing '+marker)
for job in ('FARMER','BUTCHER','SHEPHERD','FISHERMAN','BREEDER','BEEKEEPER'):
    if f'INDUSTRY_{job}_FUNCTIONAL_PASSED' not in text:fail('Industry client check missing '+job)
for job in ('BAKER','CONFECTIONER','GRILL_COOK'):
    if f'COMMERCE_{job}_CHAIN_AND_TIMER_PASSED' not in text:fail('Commercial client chain missing '+job)
for marker in ('MILITARY_GRAPHICAL_COMBAT_AND_XP_PASSED','MILITARY_GUARD_RETURN_PASSED','MILITARY_FREE_FOLLOW_AND_TRANSFORM_PASSED','MILITARY_VANILLA_CURE_PASSED'):
    if marker not in text:fail('Military client check missing '+marker)
for marker in ('BUILDER_CAPTURE_AND_GLOBAL_STOCK_PASSED','BUILDER_PRINT_AND_POSITION_PASSED','BUILDER_FOUR_WORKERS_AND_FLIGHT_PASSED','BUILDER_PARTIAL_PROJECT_SAVED'):
    if marker not in text:fail('Builder client check missing '+marker)
print('Real Minecraft client captured all required scenes; image inspection is still required.')
if 'KRAFTCITY_LIMITS_TOGGLE_AND_REMOVAL_PASSED' not in text:
    fail('Residence limit toggle/removal check did not complete.')

# Reopen the same saved world in a fresh Minecraft JVM, without copying/resetting it.
restore_log=Path('client-restore-console.log')
restore_started=time.time()
with restore_log.open('w') as out:
    restored=subprocess.run(['xvfb-run','-a','-s','-screen 0 1920x1080x24','bash','gradlew','--no-daemon','-Pvisual_check=true','-Pvisual_restore=true','runClient'],stdout=out,stderr=subprocess.STDOUT,timeout=180)
restored_text=restore_log.read_text(errors='replace')
print('\n'.join(restored_text.splitlines()[-25:]))
if 'TEN_SLOTS_WORLD_RESTART_PASSED' not in restored_text:
    fail('Ten stored output stacks did not survive a complete Minecraft process restart.')
if 'EMPLOYMENT_DISMISS_WORLD_RESTART_PASSED' not in restored_text:fail('Dismissed resident history or fee did not survive process restart.')
if 'EMPLOYMENT_WORLD_RESTART_PASSED' not in restored_text:
    fail('Employment inventory/progression did not survive process restart.')
if 'WORKFORCE_WORLD_RESTART_PASSED' not in restored_text:
    fail('Workforce state did not survive a complete Minecraft process restart.')
if 'FARMER_SAME_WORLD_RESTART_PASSED' not in restored_text:fail('Farmer persistence did not survive process restart.')
if 'FARMER_AUTHORIZED_ENDER_RESTART_PASSED' not in restored_text:fail('The authorized Ender chest did not survive reopening.')
if 'FARMER_THREE_SLEEP_AFTER_RESTART_PASSED' not in restored_text:fail('The three saved farmers did not sleep again after reopening.')
if 'INDUSTRY_SIX_JOBS_WORLD_RESTART_PASSED' not in restored_text:
    fail('Six industrial jobs did not survive process restart.')
if 'MILITARY_WORLD_RESTART_PASSED' not in restored_text:fail('Military did not survive JVM restart.')
for marker in ('COMMERCE_THREE_CHAINS_WORLD_RESTART_PASSED','COMMERCE_PARTIAL_RECIPE_RESUMED_ONCE_PASSED'):
    if marker not in restored_text:fail('Commercial restart missing '+marker)
for marker in ('BUILDER_WORLD_RESTART_PASSED','BUILDER_13_BLOCKS_RESERVE_RESUPPLY_PASSED','BUILDER_RESUMED_ONCE_PASSED'):
    if marker not in restored_text:fail('Builder restart missing '+marker)
if restored.returncode and 'Gradle build daemon disappeared unexpectedly' not in restored_text:
    fail(restored.returncode)
p=Path('build/screenshots/visual-anchor-restored.png')
if not p.exists() or p.stat().st_mtime < restore_started:fail('Fresh restored anchor screenshot missing.')
print('Fresh Minecraft process verified all ten stored stacks after reopening the saved world.')

p=Path('build/screenshots/visual-builder-progress-restored.png')
if not p.exists() or p.stat().st_mtime < restore_started:fail('Fresh builder progress screenshot missing.')
data=p.read_bytes()
if data[:8]!=bytes.fromhex('89504e470d0a1a0a') or struct.unpack('>II',data[16:24])!=(1600,900):fail('Invalid restored builder screenshot.')
if 'BUILDER_PROGRESS_SCREEN_PASSED' not in restored_text:fail('Builder progress selection did not pass.')
