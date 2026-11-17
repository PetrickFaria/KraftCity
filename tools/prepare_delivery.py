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
for stage in ('01','02'):
    shutil.copy2(f'docs/KRAFTCITY-ETAPA-{stage}.md',out/f'KRAFTCITY-ETAPA-{stage}.md')
sha=os.environ['GITHUB_SHA']
match=re.search(r'All (\d+) required tests passed',Path('gametest-console.log').read_text(errors='replace'))
assert match, 'GameTest success evidence missing'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(out/'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
(out/f'relatorio-{version}.md').write_text(f"""# KraftCity {version} — etapa 02

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17.

- Base: KraftCity 1.0.95, commit aprovado 7ae2ad9fa13102abe581bacd2dd4fe1d025a0f2f.
- Residencial preservado; eventos de ciclo de vida e indicador de trabalho integram os postos novos.
- Industrial: seis opções reais, contratação por 10 esmeraldas, um trabalhador por posto e um posto por trabalhador.
- Comercial: três opções de tela; preço definitivo pendente, contratação e cobrança desabilitadas por padrão.
- Blocos profissionais são registros distintos, com o mesmo inventário acessível pelo bloco e trabalhador.
- Inventário 3x9 + quatro slots específicos reservados + slot exclusivo para esmeraldas.
- SavedData único do posto; objeto recuperável leva somente UUID, impedindo duplicação por cópia do token.
- História de nível/XP por profissão preservada na perda de vínculo e recontratação; sem regras de ganho de XP/produção.
- {match.group(1)} Forge GameTests obrigatórios aprovados, mais os 12 verificadores estáticos do workflow.
- Cliente gráfico: contratação por botão/rede, duplo clique, inventário compartilhado, quebra/recolocação,
  cobrança comercial forjada recusada, recontratação e indicador residencial verde.
- Reabertura em outra JVM: inventário, associação, nível/XP, moradia/cama e pendência comercial verificados.
- Ingredientes/receitas dos novos blocos, filtros/produção, propriedade de postos e progressão ficam pendentes;
  as receitas antigas continuam preservadas. Novos blocos genéricos disponíveis no Criativo/comandos.
- EMF/ETF/Fresh Animations e sessões dedicadas com dois clientes humanos não foram revalidados.
- Decisões, limites e estratégia exata de serialização: KRAFTCITY-ETAPA-02.md incluído.

Commit: {sha}.
SHA-256 JAR: {digest}.
""",encoding='utf-8')
print('Delivery prepared:',jar.name,'commit',sha,'SHA-256',digest)
