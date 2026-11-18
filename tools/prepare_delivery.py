from pathlib import Path
import os, hashlib, subprocess, shutil, re

version=dict(line.split('=',1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
out=Path('artifact-output')
if out.exists():shutil.rmtree(out)
out.mkdir()
jar=Path(f'build/libs/kraftcity-{version}.jar')
shutil.copy2(jar,out/jar.name)
subprocess.run(['git','archive','--format=zip','--output='+str(out/f'kraftcity-{version}-source.zip'),'HEAD'],check=True)
for name in ['gametest-console.log','client-console.log','client-restore-console.log']:
    p=Path(name)
    if p.exists():shutil.copy2(p,out/name)
if Path('build/screenshots').exists():shutil.copytree('build/screenshots',out/'screenshots',dirs_exist_ok=True)
for stage in ('01','02','03','04','05','06'):
    shutil.copy2(f'docs/KRAFTCITY-ETAPA-{stage}.md',out/f'KRAFTCITY-ETAPA-{stage}.md')
shutil.copytree('docs/licenses',out/'licenses',dirs_exist_ok=True)
sha=os.environ['GITHUB_SHA']
match=re.search(r'All (\d+) required tests passed',Path('gametest-console.log').read_text(errors='replace'))
assert match, 'GameTest success evidence missing'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(out/'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
(out/f'relatorio-{version}.md').write_text(f"""# KraftCity {version} — etapa 06

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17.

- Base 1.0.99: código 674e55e69446a2bfbefed6249113b303658a5150, documentação b53a8992f9091efa202e53e478c8b5c10e0baa48; branch feature/kraftcity-builder.
- Sistemas residenciais, industriais, comerciais e militares preservados.
- Contratação por 10 esmeraldas, posto profissional, vínculos e progressão existentes.
- Estoque global 3×9, tinta 1000, carga 500, papéis/biblioteca/obras compartilhados entre postos e dimensões.
- Captura sanitizada e arquivos StructureTemplate NBT compatíveis com Create 0.5.0.i, sem dependência do Create/Flywheel.
- Gravação em 100 ticks, tinta proporcional, prévia de modelos semitransparente, posição/rotação/espelho confirmados no servidor.
- Cooperação: seis convocados até 150 blocos, quatro posições, navegação física, coleta em baús/barris até cinco blocos do posto.
- Preservar padrão, Substituir com proteções, materiais/carga/XP somente por colocação aceita.
- Níveis cumulativos 0/50/150/300/500 blocos; 100/80/60/40/20 ticks por bloco.
- Pausa, cancelamento e retomada da mesma obra; tarefas parciais e recibos persistentes, sem tempo offline.
- Braços alternados, voos visuais sem ItemEntities, indicador de progresso limitado a 64 blocos.
- {match.group(1)} GameTests obrigatórios aprovados, incluindo os 152 anteriores, e 12 verificadores estáticos.
- Cliente gráfico: todas as cenas anteriores + dez cenas do Construtor; segunda JVM verifica retomada sem duplicação.
- Namespace e schemas anteriores preservados; novo kraftcity_builder schema 1; emprego v5 e builder v1.
- Receitas de fabricação não especificadas: itens/blocos disponíveis no Criativo/comandos, sem ingredientes arbitrários.
- Limites, privacidade de NBT, casos não suportados e decisões detalhados em KRAFTCITY-ETAPA-06.md.
- Não houve sessão com dois clientes humanos nem validação exaustiva de todos os mods; concorrência é automatizada.
- Código-fonte, seis relatórios, licença de referência, logs e capturas acompanham este artefato.

Commit validado: {sha}.
SHA-256 JAR: {digest}.
""",encoding='utf-8')
print('Delivery prepared:',jar.name,'commit',sha,'SHA-256',digest)
