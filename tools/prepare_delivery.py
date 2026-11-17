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
for stage in ('01','02','03'):
    shutil.copy2(f'docs/KRAFTCITY-ETAPA-{stage}.md',out/f'KRAFTCITY-ETAPA-{stage}.md')
sha=os.environ['GITHUB_SHA']
match=re.search(r'All (\d+) required tests passed',Path('gametest-console.log').read_text(errors='replace'))
assert match, 'GameTest success evidence missing'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(out/'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
(out/f'relatorio-{version}.md').write_text(f"""# KraftCity {version} — etapa 03

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17.

- Base aprovada: 1.0.96, 0cc687b71516d89a5acbd2927ac73353408f0b9e; branch feature/kraftcity-industry.
- Seis profissões industriais: Fazendeiro, Açougueiro, Pastoreiro, Pescador, Criador e Apicultor.
- Contratos, cobrança industrial de 10 esmeraldas, UUIDs e inventário 3x9 + cinco slots específicos preservados.
- Área 5x5..13x13 por nível, seleção menor no Lv5, limites azuis independentes dos verdes residenciais.
- Desgaste fracionário, colheitas/tosas 25/50/75/100, histórico persistente por profissão.
- Culturas maduras/replantio, irrigação sem fonte, reprodução com pares e capacidade, lã sem abate,
  peixe somente com água real, lobos com cuidador lógico sem UUID de jogador, mel com fumaça e prioridade de frascos.
- Descarte associado a baú/barril, reserva plantável, retirada de até cinco insumos no amanhecer, retorno residencial no entardecer.
- Ferramentas de esmeralda, variantes customizadas de tesouras/varas, receitas, modelos tingidos e nomes em português.
- SavedData schema 1 aditivo; token recuperável continua UUID-only, sem cópia de inventário nem recontratação automática.
- {match.group(1)} GameTests obrigatórios aprovados, preservando 62 anteriores, e 12 verificadores estáticos.
- Cliente gráfico: cenas e marcadores reais das seis profissões, GUI sincronizada, regressões de contratação/descarte
  e reabertura em outra JVM verificando postos, produtos, inventários, vínculos, nível/XP e dados residenciais.
- Regras provisórias configuráveis, detalhes dos tiers, suporte de culturas e integração com proteção: KRAFTCITY-ETAPA-03.md.
- Preço/produção comercial, Militar e Construtor não implementados. Sem testes novos de packs EMF/ETF/Fresh Animations
  nem sessão com dois clientes humanos; concorrência coberta no servidor e pacotes reais do cliente.

Commit validado: {sha}.
SHA-256 JAR: {digest}.
""",encoding='utf-8')
print('Delivery prepared:',jar.name,'commit',sha,'SHA-256',digest)
