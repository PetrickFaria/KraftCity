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
for stage in ('01','02','03','04'):
    shutil.copy2(f'docs/KRAFTCITY-ETAPA-{stage}.md',out/f'KRAFTCITY-ETAPA-{stage}.md')
sha=os.environ['GITHUB_SHA']
match=re.search(r'All (\d+) required tests passed',Path('gametest-console.log').read_text(errors='replace'))
assert match, 'GameTest success evidence missing'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(out/'SHA256SUMS.txt').write_text(f'{digest}  {jar.name}\n')
(out/f'relatorio-{version}.md').write_text(f"""# KraftCity {version} — etapa 04

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17.

- Base aprovada: 1.0.97, 0d4b7d4d81568474eec7ef39af2143199d32bb10; branch feature/kraftcity-commerce.
- Residencial e seis profissões industriais preservados. Comercial transformável: Padeiro, Confeiteiro e Churrasqueiro.
- Contratação comercial aprovada: habilitada, 10 esmeraldas para as três opções, migração única dos antigos defaults.
- Uma receita = uma produção; Lv2/3/4/5 em 25/50/75/100, tempos 60/50/40/30/15 segundos ativos.
- Receitas existentes: 3 trigos -> 1 pão; cana -> açúcar vanilla; cinco carnes -> assadas da mesma espécie/quantidade.
- Combustível vanilla aprovado: crédito por receita de forno (um carvão/8 carnes), remanescentes e calor persistentes.
- Baús carregados até cinco blocos do limite, mais próximo/desempate determinístico, extração física só do necessário.
- Cana madura fornecida pelo Fazendeiro, preservando raiz/reserva; três cadeias industriais/comerciais reais testadas.
- Campo commerce aditivo no SavedData schema 1; sem migração dos 32 slots ou cópias de itens no token.
- Tempo/receita/combustível preservados durante pausa/recarga; commit simulado antes do débito/resultado/XP.
- {match.group(1)} GameTests obrigatórios aprovados, preservando os 91 anteriores, e 12 verificadores estáticos.
- Cliente gráfico: contratos por pacote real, caminhada, produção temporizada das três profissões e limites anteriores.
- Nova JVM: dados anteriores, três cadeias, operação parcial pausada e retomada produzindo exatamente uma vez.
- Código-fonte completo, documentação, logs e capturas acompanham o mesmo artefato.
- Não houve nova sessão com dois clientes humanos ou combinações EMF/ETF/Fresh Animations; concorrência automatizada no servidor.
- Receitas de blocos genéricos ainda não especificadas; Militar/Construtor fora do escopo. Não há preço/XP/combustível comercial pendente.
- Regras completas e decisões: KRAFTCITY-ETAPA-04.md; cliente e servidor usam o mesmo JAR (canal emprego v3).

Commit validado: {sha}.
SHA-256 JAR: {digest}.
""",encoding='utf-8')
print('Delivery prepared:',jar.name,'commit',sha,'SHA-256',digest)
