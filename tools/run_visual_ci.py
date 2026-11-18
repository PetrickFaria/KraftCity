"""Start a real graphical Minecraft client and require its captured inspection scenes."""
from pathlib import Path
import subprocess
import shutil
import sys
import struct
import time

if not shutil.which('xvfb-run'):
    sys.exit('Xvfb is unavailable.')
source=Path('run-gametest/world')
if not source.exists():
    sys.exit('GameTest world unavailable for graphical inspection.')
# Replace this generated fixture before the first JVM; the second JVM below reopens it unchanged.
visual_world=Path('run/saves/visual-check')
if visual_world.exists():shutil.rmtree(visual_world)
shutil.copytree(source,visual_world)
# Inspect the unchanged Portuguese UI labels instead of untranslated English keys.
Path('run').mkdir(exist_ok=True)
Path('run/options.txt').write_text('lang:pt_br\nguiScale:3\nsimulationDistance:8\n')
log=Path('client-console.log')
started=time.time()
with log.open('w') as out:
    result=subprocess.run(['xvfb-run','-a','-s','-screen 0 1920x1080x24','bash','gradlew','--no-daemon','-Pvisual_check=true','runClient'],stdout=out,stderr=subprocess.STDOUT)
text=log.read_text(errors='replace')
print('\n'.join(text.splitlines()[-35:]))
for name in ['visual-chests.png','visual-doors.png','visual-anchor-slots.png','visual-reference-door.png','visual-chest-cavity.png','visual-courier-deposit.png','visual-courier-closed.png','visual-steve-opacity-multiple.png','visual-kraftcity-limits-two.png','visual-kraftcity-limits-removed.png','visual-kraftcity-limits-off.png','visual-industrial-hiring.png','visual-farmer-inventory.png','visual-worker-inventory.png','visual-replaced-workstation.png','visual-commercial-pending.png','visual-rehired-progress.png','visual-residential-employed.png','visual-industry-blue-limits.png','visual-military-hiring.png','visual-military-soldier.png','visual-military-blacklist.png','visual-military-guard.png','visual-military-zombie.png','visual-builder-hiring.png','visual-builder-global.png','visual-builder-global-fifty.png','visual-builder-capture.png','visual-builder-materials.png','visual-builder-print.png','visual-builder-hologram.png','visual-builder-cooperative.png','visual-builder-flight.png','visual-commerce-hiring.png','visual-commerce-baker.png','visual-commerce-confectioner.png','visual-commerce-grill_cook.png']+[f'visual-industry-{name}.png' for name in ('farmer','butcher','shepherd','fisherman','breeder','beekeeper')]+[f'visual-steve-opacity-{percent}.png' for percent in (0,10,25,50,75,90,100)]:
    path=Path('build/screenshots')/name
    if not path.exists() or path.stat().st_mtime < started:sys.exit('Minecraft did not capture a fresh '+name)
    data=path.read_bytes()
    if data[:8]!=bytes.fromhex('89504e470d0a1a0a') or struct.unpack('>II',data[16:24])!=(1600,900):
        sys.exit('Invalid Minecraft screenshot: '+name)
if result.returncode and 'Gradle build daemon disappeared unexpectedly' not in text:
    sys.exit(result.returncode)
for marker in ['COURIER_PLAYER_OVERLAP_PASSED','COURIER_CLIENT_OPEN_CLOSE_PASSED','COURIER_GRAPHICAL_DEPOSIT_PASSED','STEVE_WORLD_OPACITY_50_PASSED','STEVE_WORLD_OPACITY_PERCENTAGES_PASSED','STEVE_MULTI_RESIDENT_OPACITY_SYNC_PASSED','STEVE_MULTI_RESIDENT_OPACITY_CAPTURED']:
    if marker not in text:sys.exit('Minecraft client check missing '+marker)
for marker in ['EMPLOYMENT_GRAPHICAL_CONTRACT_PASSED','EMPLOYMENT_INVENTORY_SHARED_PASSED','EMPLOYMENT_PORTABLE_INVENTORY_PASSED','COMMERCIAL_PENDING_NO_CHARGE_PASSED','EMPLOYMENT_REHIRE_PROGRESS_PASSED','EMPLOYMENT_RESIDENTIAL_INDICATOR_PASSED']:
    if marker not in text:sys.exit('Employment client check missing '+marker)
if 'INDUSTRY_BLUE_LIMITS_PASSED' not in text:sys.exit('Blue work area toggle was not validated.')
for job in ('FARMER','BUTCHER','SHEPHERD','FISHERMAN','BREEDER','BEEKEEPER'):
    if f'INDUSTRY_{job}_FUNCTIONAL_PASSED' not in text:sys.exit('Industry client check missing '+job)
for job in ('BAKER','CONFECTIONER','GRILL_COOK'):
    if f'COMMERCE_{job}_CHAIN_AND_TIMER_PASSED' not in text:sys.exit('Commercial client chain missing '+job)
for marker in ('MILITARY_GRAPHICAL_COMBAT_AND_XP_PASSED','MILITARY_GUARD_RETURN_PASSED','MILITARY_FREE_FOLLOW_AND_TRANSFORM_PASSED','MILITARY_VANILLA_CURE_PASSED'):
    if marker not in text:sys.exit('Military client check missing '+marker)
for marker in ('BUILDER_CAPTURE_AND_GLOBAL_STOCK_PASSED','BUILDER_PRINT_AND_POSITION_PASSED','BUILDER_FOUR_WORKERS_AND_FLIGHT_PASSED','BUILDER_PARTIAL_PROJECT_SAVED'):
    if marker not in text:sys.exit('Builder client check missing '+marker)
print('Real Minecraft client captured all required scenes; image inspection is still required.')
if 'KRAFTCITY_LIMITS_TOGGLE_AND_REMOVAL_PASSED' not in text:
    sys.exit('Residence limit toggle/removal check did not complete.')

# Reopen the same saved world in a fresh Minecraft JVM, without copying/resetting it.
restore_log=Path('client-restore-console.log')
restore_started=time.time()
with restore_log.open('w') as out:
    restored=subprocess.run(['xvfb-run','-a','-s','-screen 0 1920x1080x24','bash','gradlew','--no-daemon','-Pvisual_check=true','-Pvisual_restore=true','runClient'],stdout=out,stderr=subprocess.STDOUT,timeout=180)
restored_text=restore_log.read_text(errors='replace')
print('\n'.join(restored_text.splitlines()[-25:]))
if 'TEN_SLOTS_WORLD_RESTART_PASSED' not in restored_text:
    sys.exit('Ten stored output stacks did not survive a complete Minecraft process restart.')
if 'EMPLOYMENT_WORLD_RESTART_PASSED' not in restored_text:
    sys.exit('Employment inventory/progression did not survive process restart.')
if 'WORKFORCE_WORLD_RESTART_PASSED' not in restored_text:
    sys.exit('Workforce state did not survive a complete Minecraft process restart.')
if 'INDUSTRY_SIX_JOBS_WORLD_RESTART_PASSED' not in restored_text:
    sys.exit('Six industrial jobs did not survive process restart.')
if 'MILITARY_WORLD_RESTART_PASSED' not in restored_text:sys.exit('Military did not survive JVM restart.')
for marker in ('COMMERCE_THREE_CHAINS_WORLD_RESTART_PASSED','COMMERCE_PARTIAL_RECIPE_RESUMED_ONCE_PASSED'):
    if marker not in restored_text:sys.exit('Commercial restart missing '+marker)
for marker in ('BUILDER_WORLD_RESTART_PASSED','BUILDER_13_BLOCKS_RESERVE_RESUPPLY_PASSED','BUILDER_RESUMED_ONCE_PASSED'):
    if marker not in restored_text:sys.exit('Builder restart missing '+marker)
if restored.returncode and 'Gradle build daemon disappeared unexpectedly' not in restored_text:
    sys.exit(restored.returncode)
p=Path('build/screenshots/visual-anchor-restored.png')
if not p.exists() or p.stat().st_mtime < restore_started:sys.exit('Fresh restored anchor screenshot missing.')
print('Fresh Minecraft process verified all ten stored stacks after reopening the saved world.')

p=Path('build/screenshots/visual-builder-progress-restored.png')
if not p.exists() or p.stat().st_mtime < restore_started:sys.exit('Fresh builder progress screenshot missing.')
data=p.read_bytes()
if data[:8]!=bytes.fromhex('89504e470d0a1a0a') or struct.unpack('>II',data[16:24])!=(1600,900):sys.exit('Invalid restored builder screenshot.')
if 'BUILDER_PROGRESS_SCREEN_PASSED' not in restored_text:sys.exit('Builder progress selection did not pass.')
