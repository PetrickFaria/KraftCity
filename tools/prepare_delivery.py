from pathlib import Path
import os, hashlib, subprocess, shutil, re

version=dict(line.split('=',1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
out=Path('artifact-output');out.mkdir(exist_ok=True)
jar=Path(f'build/libs/kraftcity-{version}.jar')
shutil.copy2(jar,out/jar.name)
subprocess.run(['git','archive','--format=zip','--output='+str(out/f'kraftcity-{version}-source.zip'),'HEAD'],check=True)
for name in ['gametest-console.log','client-console.log','client-restore-console.log']:
    p=Path(name)
    if p.exists():shutil.copy2(p,out/name)
if Path('build/screenshots').exists():shutil.copytree('build/screenshots',out/'screenshots',dirs_exist_ok=True)
for stage in ('01','02','03','04','05'):
    shutil.copy2(f'docs/KRAFTCITY-ETAPA-{stage}.md',out/f'KRAFTCITY-ETAPA-{stage}.md')
sha=os.environ['GITHUB_SHA']
match=re.search(r'All (\d+) required tests passed',Path('gametest-console.log').read_text(errors='replace'))
assert match, 'GameTest success evidence missing'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(out/'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
(out/f'relatorio-{version}.md').write_text(f"""# KraftCity {version} — etapa 05

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17.

- Base aprovada 1.0.98, 3ea9d2a8548e27a9bf1e8f17aed3de3b8a19dad8; branch feature/kraftcity-military.
- Sistemas residenciais, seis profissões industriais e três comerciais preservados.
- Militar funcional: 15 esmeraldas, um soldado por posto, bloco físico profissional.
- Livre/Guarda/Seguir; Seguir exige ataque real do jogador e seleção da criatura.
- Blacklist categorizada, Minecraft/Mods e busca, Creeper desmarcado por padrão.
- Inventário de 32 slots preservado + seis equipamentos militares; schema 1 aditivo.
- Combate, munição, durabilidade, encantamentos, XP compartilhado e turnos/cama.
- Lv1..5 em 0/75/150/225/300 XP: 5/6/7/8/9 corações; XP em milésimos para preservar frações.
- Soldado Zumbi com UUID/posto/itens/histórico preservados; Fraqueza/Maçã Dourada e cura vanilla.
- Morte definitiva respeita doMobLoot/Maldição do Desaparecimento, sem duplicar inventário.
- {match.group(1)} GameTests obrigatórios aprovados e 12 verificadores estáticos.
- Cliente gráfico: regressões anteriores e contratos/equipamentos/Blacklist/combate/retorno/cura militares.
- Nova JVM confirma inventário, XP, vínculos, modos e turnos militares, além dos marcadores anteriores.
- Provisórios configuráveis: cura normal 200 ticks, contribuição/ordens 1200 ticks, alcance operacional 48.
- Sem receita arbitrária do Militar genérico; Criativo/comandos. Construtor fora do escopo.
- Não houve sessão com dois clientes humanos nem testes de todos os mods de criaturas; concorrência automatizada.
- Regras e retomada: KRAFTCITY-ETAPA-05.md; cliente e servidor com o mesmo JAR, emprego v4/militar v1.
- Código-fonte, documentação, logs e capturas acompanham o artefato.

Commit validado: {sha}.
SHA-256 JAR: {digest}.
""",encoding='utf-8')
print('Delivery prepared:',jar.name,'commit',sha,'SHA-256',digest)
