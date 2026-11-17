"""Require actual GameTest completion; tolerate only Gradle's post-success daemon shutdown."""
from pathlib import Path
import subprocess
import re
import sys
import time

run=Path('run-gametest');run.mkdir(exist_ok=True)
(run/'server.properties').write_text('level-type=minecraft:flat\ngenerator-settings={}\ngenerate-structures=false\nview-distance=5\nsimulation-distance=5\n')
log_path=Path('gametest-console.log')
started=time.time()
with log_path.open('w') as out:
    result=subprocess.run(['bash','gradlew','--no-daemon','runGameTestServer'],stdout=out,stderr=subprocess.STDOUT)
text=log_path.read_text(errors='replace')
print('\n'.join(text.splitlines()[-110:]))
server_log=Path('run-gametest/logs/latest.log')
if not server_log.exists() or server_log.stat().st_mtime < started:
    sys.exit('Forge did not write a fresh server log for this run.')
evidence=server_log.read_text(errors='replace') if server_log.exists() else ''
completed=re.search(r'\b(\d+) GAME TESTS COMPLETE\b',evidence)
passed=re.search(r'All (\d+) required tests passed',evidence)
if not completed or not passed or completed.group(1)!=passed.group(1):
    sys.exit('Forge server did not confirm that all completed GameTests passed.')
expected=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in Path('src/gametest/java').rglob('*.java'))
if int(passed.group(1)) != expected or expected == 0:
    sys.exit(f'Forge ran {passed.group(1)} tests; the selected source set contains {expected}.')
print(f"Forge confirmed all {passed.group(1)} required GameTests passed.")
if result.returncode and 'Gradle build daemon disappeared unexpectedly' not in text:
    sys.exit(result.returncode)
if result.returncode:
    print(f"Forge confirmed all {passed.group(1)} GameTests passed; Gradle reported daemon shutdown afterward.")
