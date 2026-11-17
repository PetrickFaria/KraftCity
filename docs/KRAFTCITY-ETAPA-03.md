# KraftCity — Etapa 03: seis profissões industriais

## Continuidade

Base de código validada: `0cc687b71516d89a5acbd2927ac73353408f0b9e`,
1.0.96, [CI 44005876367](https://github.com/PetrickFaria/KraftCity/actions/runs/44005876367).
A inspeção de refs, commits, PRs, Actions e artefatos confirmou apenas o commit
posterior de documentação `4144fca`. `main` continua em 1.0.23 e não foi usada.
Trabalho retomado na branch `feature/kraftcity-industry`, preservando também
as alterações locais da execução interrompida, sem reinicializar a Etapa 02.

## Uso

Contrate pelo Industrial existente, por 10 esmeraldas. O próprio bloco
profissional e seu aldeão abrem o mesmo inventário e as mesmas opções. Há
27 slots comuns, ferramenta (27), dois insumos (28/29), equipamento/terceiro
insumo (30) e esmeraldas (31). Nenhum índice antigo mudou; no Fazendeiro,
somente a posição visual do slot 30 foi movida para o canto superior direito.
Os slots reservados continuam aceitando itens antigos: insumos incorretos
não são destruídos nem usados como se fossem válidos.

Os botões 5×5, 7×7, 9×9, 11×11 e 13×13 obedecem Lv1–Lv5. Até Lv4 a área é
exatamente a do nível; Lv5 permite escolher áreas menores. Limites alterna
marcações azuis transparentes, independentes do sistema verde residencial.
A planta é centrada no posto; ações de culturas/colmeias pesquisam ±2 blocos
verticais, animais usam a caixa de ±3 blocos verticais. A pesca tem busca
própria descrita abaixo. Sem alcance inventado entre emprego e residência.

**Vincular baú:** clique no botão e depois em um baú vanilla (inclusive duplo)
ou barril dentro da área. Esse recipiente passa a ser o Baú de Descarte;
não há cópia de seu inventário. Agachar e clicar no posto encerra modos de
associação. Ao entardecer (12000), excedentes são transferidos ao recipiente,
e o aldeão retorna à residência; a rotina anterior assume chuva, portas,
cama e sono. Baú ausente, descarregado ou cheio não apaga itens: eles ficam
no inventário do posto. Não se inventou receita de um novo tipo de baú.

Alertas vão apenas aos jogadores da mesma dimensão a até 64 blocos do posto,
identificando profissão/trabalhador. Cada causa tem limite de um aviso a cada
300 ticks (15 s a 20 TPS) por trabalhador/posto; nunca broadcast global.

## Profissões

| Profissão | Operação e requisitos |
| --- | --- |
| Fazendeiro | Enxada, farinha de osso e balde de água migram do 3×9 para slots vazios. Só colhe culturas maduras; reserva uma semente e replanta a mesma espécie antes de avançar. Farinha de osso é aplicada à cultura atual até amadurecer. Planta em solo livre com sementes existentes, sem arar uma área inteira sem insumos. |
| Açougueiro | Machado e três alimentos de reprodução. Administra vacas, porcos, ovelhas, galinhas e coelhos cadastrados pelo jogador; filhotes não contam no limite adulto. Reproduz pares da mesma espécie; excedentes adultos são abatidos por idade conhecida, deixando pelo menos dois adultos da espécie. Ordenha uma vaca adulta real com balde vazio, no máximo uma coleta por minuto por posto e por vaca. |
| Pastoreiro | Tesoura e trigo; tosquia ovelhas prontas sem matá-las, preservando sua cor. Reprodução somente enquanto adultos + filhotes forem inferiores à capacidade. |
| Pescador | Vara utilizável, volume livre real de água 3×3×3, inteiramente dentro da planta e na altura do posto ou abaixo. Busca candidatos de −9 a +2, verificando o volume de −10 a +3, descartando volumes cuja superfície fique acima do posto. Usa a loot table vanilla de peixes, sem simular tesouros ou pescar sem água. |
| Criador | Ossos para domesticação lógica e cuidado, carne apropriada para reprodução. Só domestica lobos sem dono, não agressivos; não toma lobos já domesticados nem atribui UUID de jogador ao aldeão. Lobos de jogador não são usados para reprodução automática. |
| Apicultor | Colmeia/ninho real com mel 5, fumaça de fogueira e espaço de saída. Prioriza frascos vazios do 3×9, move um ao slot, guarda o mel no 3×9 e repete; só então usa tesoura para três favos. Reprodução usa flores, pares disponíveis e capacidade livre, contando também ocupantes de colmeias conhecidas. Nenhuma colmeia/abelha é destruída. |

No Açougueiro, use **Cadastrar animais**, clique nos animais que colocou na
área e agache/clique no posto para encerrar. O vínculo é persistente por UUID
do posto; filhotes gerados recebem o mesmo vínculo. Isso resolve explicitamente
a distinção entre animais fornecidos pelo jogador e animais selvagens. Idade
conhecida usa nascimento registrado para filhotes industriais e idade observada
no cadastro para animais já adultos; vanilla não fornece data de nascimento
histórica de um adulto. Empates mantêm ordem de observação, sem inventar idade.

A capacidade adulta é 2/4/6/8/10. Pastoreiro/Criador não reproduzem além do
número de vagas (incluindo filhotes). Açougueiro pode gerar filhotes quando já
há um par na capacidade, com até dois filhotes simultâneos, para possibilitar
abate apenas depois de crescerem. Nenhum abate de filhote ou de espécie sem
par reprodutivo. Alimentos precisam ser aceitos pela espécie vanilla.

Ao amanhecer, Açougueiro/Pastoreiro podem retirar **até cinco itens no total**
de descarte de Fazendeiros carregados a até 64 blocos: úteis para reprodução
no Açougueiro; somente trigo no Pastoreiro. A retirada é marcada uma vez por
dia e respeita espaço, proteção e remoção real do recipiente de origem.

O Fazendeiro dá suporte às culturas vanilla `CropBlock` com semente conhecida:
trigo, cenoura, batata e beterraba. Não converte caules/frutos, cana ou blocos
de mods desconhecidos em loot fictício. Mantém subprodutos vanilla e separa
semente de replantio do rendimento KraftCity. A enxada define a quantidade
do produto principal: madeira 1, ferro 1–2, ouro 1–3, esmeralda 1–4,
diamante 1–5, netherita 1–6. Pedra usa o rendimento conservador de madeira;
não altera o tier vanilla. O machado usa a mesma regra para carne; os demais
subprodutos continuam vindo da tabela real do animal, invocada uma única vez.
A reserva de itens plantáveis no posto é de até tamanho² (25 no 5×5) no total;
o excedente é enviado ao descarte. O replantio nunca troca a espécie da cultura.

Balde de água permanece no slot, não é esvaziado nem cria fonte líquida.
O posto hidrata solo a até quatro blocos horizontais, limitado à planta e à
altura vanilla equivalente. Remover balde/quebrar posto desliga o efeito;
a umidade existente depois decai pelas regras vanilla. Índice por chunk evita
pesquisar todos os postos em cada random tick de terra arada.

Lobos KraftCity usam flag vanilla de domesticado **sem owner UUID vanilla**
e `ForgeData.KraftCityCaretaker` com UUID do aldeão. Isso inibe caça selvagem;
seguir o cuidador carregado é implementado no servidor. Não há comandos de
jogador/dono vanilla, nem transferência de lobos de jogador. O cuidador lógico
é persistente e não restaura emprego automaticamente. Quando o cuidador não
está carregado, o sistema não carrega chunks nem teleporta o animal.

## Níveis, desgaste e decisões configuráveis

Fazendeiro ganha uma ação por colheita madura concluída; Pastoreiro, por tosa.
Níveis acumulados: Lv1 inicial, Lv2 em 25, Lv3 em 50, Lv4 em 75, Lv5 em 100.
Nível/XP anteriores são preservados e não são rebaixados por uma nova ação.
Registros industriais legados com nível 0 são tratados operacionalmente e
exibidos como Lv1, sem apagar o registro nem afetar trabalhadores passivos.

Desgaste por ação em quartos de durabilidade: 6/5/4/3/2 nos níveis 1–5.
O resto de 0 a 3 é salvo no próprio ItemStack em `KraftCityWearQuarters`;
quatro ações resultam exatamente em 6/5/4/3/2 pontos antes de Unbreaking.
Unbreaking conserva seu comportamento vanilla. Ferramenta quebrada é removida;
sem ferramenta válida a ação não produz itens nem ganha XP. A mão mostra um
espelho visual sem drop da ferramenta; a autoridade permanece no posto.
A mão original é salva/restaurada no vínculo, não transferida para o inventário.

O comando não definiu todos os números. Estas são decisões **provisórias,
configuráveis e sujeitas a balanceamento**, não regras definitivas:

```toml
# world/serverconfig/kraftcity-employment-server.toml
[industria]
intervaloAcoesTicks = 20
pescaBaseTicks = 600
reproducaoTicks = 6000
acoesPorNivelOutrasProfissoes = 25
cuidadoLobosTicks = 1200
```

Pesca: T(L)=Tbase×{1,5;1,25;1;0,75;0,5}. Assim, “50% mais rápido” significa
metade do tempo, não dividir por 1,5. Com base 600, os tempos-alvo são
900/750/600/450/300 ticks, arredondados para cima ao inteiro e quantizados
pelo intervalo de operação. O progresso só avança com trabalhador, vara,
volume válido e acesso permitido; noite/chuva/falta de requisito interrompem
a tentativa. Troca de área invalida água fora do limite.

Outras profissões: uma ação por abate, captura de peixe, domesticação/cuidado
ou reprodução de lobo, e coleta de mel/favos; provisoriamente 25 por nível.
Cuidado/treino de lobo lógico consome um osso, no máximo uma vez por minuto
por lobo, para permitir progressão sem XP passivo e sem forçar reprodução além
da capacidade. Reprodução bem-sucedida tem prioridade. Probabilidade de
aceitar osso na domesticação é 1/3, como referência vanilla. Novas regras podem
ajustar esses parâmetros preservando o histórico existente.

## Ferramentas e recursos

Novos itens, todos em `residenciasaldeoes`, aba **KraftCity — Ferramentas**:
`emerald_sword`, `emerald_hoe`, `emerald_axe`; `wood_shears`, `gold_shears`,
`emerald_shears`, `diamond_shears`, `netherite_shears`; `iron_fishing_rod`,
`gold_fishing_rod`, `emerald_fishing_rod`, `diamond_fishing_rod`,
`netherite_fishing_rod`. Ferro usa a tesoura vanilla e madeira usa a vara vanilla.
Nenhum item vanilla foi sobrescrito. Todas as variantes novas têm receitas e
nomes em português. Texturas vanilla de ferramentas são reutilizadas com
coloração por material; não se afirma possuir arte original de sprites.

Durabilidades propostas madeira/ferro/ouro/esmeralda/diamante/netherita:
59/238/32/1024/1561/2031 para tesouras; vara de ferro 250 e demais variantes
seguem o material, com vara vanilla de madeira preservada em 64. Esmeralda:
velocidade 7, bônus de dano 2,5, nível de mineração 2, encantabilidade 18;
espada e machado aplicam seus modificadores próprios. Tesouras/varas customizadas
não multiplicam automaticamente lã/peixes: diferem por durabilidade; tempos
industriais seguem nível do trabalhador, não um bônus inventado de material.

Receitas propostas para validar: ferramentas de esmeralda usam o formato
vanilla de espada/enxada/machado, trocando metal por esmeraldas e mantendo
cabos. Tesouras usam duas unidades do material em diagonal. Varas customizadas
usam três unidades do material no desenho da vara e duas linhas. Netherita
usa smithing de variante de diamante + um lingote de netherita. Materiais e
atributos são explícitos no código/JSON e permanecem sujeitos a balanceamento.
Variantes de netherita resistem a fogo como itens; reparos usam o material
correspondente. A ordem de tier de esmeralda é registrada no Forge entre ferro
e diamante. Receitas residenciais antigas e pendência de preço comercial foram mantidas.

## Persistência, proteção e custo de execução

`kraftcity_employment` continua schema 1; campo opcional `industry` é aditivo,
sem mover/copyar os 32 slots, UUIDs ou dados de `kraftcity_workforce`.
Contém área/preview, cursor, descarte, temporizadores e progresso da tentativa.
O token recuperável continua contendo somente UUID; o inventário segue único
no SavedData do mundo. Recolocar não recontrata nem duplica conteúdo. Não há
migração destrutiva de saves da 1.0.96. Não houve alteração de IDs residenciais,
registries de emprego ou regras de cobrança. O canal de emprego agora é v2;
cliente e servidor devem usar a mesma versão do JAR.

Tudo executa na thread do servidor. Até quatro postos são visitados por tick,
com intervalo operacional por posto. Varredura de culturas/colmeias: até
32 posições por visita; busca de pesca: até 16 volumes candidatos de 27 blocos.
Cursores e cache de colmeias são persistentes. Cada alvo é revalidado no ato;
mutação serial da cultura/ovelha/animal/colmeia impede coleta duplicada.
Chunks não são carregados à força e descarregamento não significa morte.

Ações respeitam `mobGriefing`, borda do mundo, eventos Forge de quebra,
colocação, interação/ataque/morte e nascimento, além do evento cancelável
`WorkActionEvent` para integrações de terrenos. Mods de proteção podem vetar
esse evento para pesca, ordenha, baú, adubação e demais ações. Não se presume
que todo mod de proteção de terceiros reconheça automaticamente FakePlayers;
é necessário validar a combinação concreta de mods. Uma recusa não consome
ferramenta, alimento, pagamento nem colheita.

## Evidências e retomada

Todos os 62 GameTests anteriores são mantidos. A suíte adiciona cenários de
produção das seis profissões, níveis/áreas, ferramentas quebradas, desgaste
fracionário após NBT, reservas/descarte, amanhecer/entardecer, proteção,
concorrência e compatibilidade schema 1. O runner gráfico mantém as cenas
anteriores e acrescenta uma tela sincronizada de cada profissão após resultado
real; outra JVM verifica persistência dos seis postos e produtos.

Comandos: build com Java 17/Forge 43.5.0; os 12 verificadores do workflow;
`python3 tools/run_gametest_ci.py`; com Xvfb/OpenGL,
`LIBGL_ALWAYS_SOFTWARE=1 python3 tools/run_visual_ci.py`.

Resultado final, commit validado, CI, artefato e hash serão registrados somente
após a validação completa. Comercial/produção de alimentos da Etapa 04,
Militar e Construtor não são implementados nesta etapa.

### Matriz de regressão da etapa

| Área | Verificação executável |
| --- | --- |
| Base residencial/arquitetura/emprego | 62 GameTests anteriores, 12 verificadores estáticos, mesmas cenas gráficas de slots, mensageiro, skins/opacidade, limites verdes, contratação e recuperação |
| Fazendeiro | Imaturidade, replantio da mesma espécie, 100 colheitas e quatro limiares, insumos automáticos, irrigação/removal, falta de sementes, inventário cheio |
| Açougueiro | Adulto mais velho, par sobrevivente, filhote protegido, animal não cadastrado, espécie sem par, reprodução, leite/cooldown, troca com inventário cheio, alimentos em slots separados |
| Pastoreiro | Tosa sem matar, expansão/capacidade, 100 tosas, ferramenta quebrada, frações nos cinco níveis, concorrência entre dois postos |
| Pescador | 27 blocos de água válidos, volume interrompido, loot real de peixe, cinco durações matemáticas |
| Criador | Domesticação lógica persistente, dono de jogador preservado, filhote real, consumo/cooldown de cuidado |
| Apicultor | Dois frascos antes da tesoura, favos, fumaça, inventário cheio sem perda, reprodução com flores |
| Comum | Seis menus compartilhados, schema antigo sem campo novo, nível/área, reserva/descarte, cinco itens no amanhecer, parada no entardecer, veto de proteção, alerta limitado, requisitos indispensáveis, receitas/tiers |
| Cliente/recarga | Uma cena real por profissão; botão de limites azuis; reabertura em outra JVM; itens, postos, empregos, XP, moradia/cama e dez slots residenciais preservados |

Os runners descartam apenas seus mundos gerados `run-gametest/world` e
`run/saves/visual-check` antes da primeira execução, evitando resíduos entre
rodadas. O segundo cliente reabre o mesmo save, sem limpeza/cópia intermediária.
Após as checagens de mensageiro e opacidade, o cenário gráfico encerra o vínculo
dos seus atores na residência original antes de iniciar outro dia de trabalho;
não repõe nem altera suas dez pilhas, que são conferidas antes/depois das seis
profissões e novamente após a JVM reiniciar. Isso impede que o próprio cenário
retire legitimamente os itens reservados para a verificação de persistência.

### Resultado local antes da publicação

- **91/91 GameTests obrigatórios aprovados**: 62 anteriores + 29 novos.
- Os 12 verificadores estáticos do workflow foram aprovados.
- Runner gráfico completo terminou com saída zero: cenas anteriores, seis
  marcadores de produção industrial real, limite azul ligado/desligado e
  33 capturas em 1600×900; as seis telas industriais foram inspecionadas.
- Quatro marcadores de recarga aprovados em outra JVM: dez slots residenciais,
  Workforce, emprego/moradia/cama da Etapa 02 e seis profissões industriais.
- Os ajustes de isolamento corrigiram resíduos de mundos de teste e a
  sobreposição entre atores de cenários; nenhuma regra residencial foi alterada
  para fazer o teste passar. O bônus Lv5 de meia durabilidade foi preservado,
  e o teste de quebra foi corrigido para executar duas tosas reais.
- O runner de GameTests reconheceu a mensagem de 91 testes aprovados no log
  fresco do Forge; a JVM de testes encerrou o daemon Gradle depois do sucesso,
  condição já tratada pelo runner anterior. A build do JAR é verificada
  separadamente e não depende dessa tolerância.
- Não foram realizadas novas sessões com dois clientes humanos nem validações
  de combinações EMF/ETF/Fresh Animations; concorrência é exercitada no servidor,
  além dos pacotes reais do cliente gráfico.
- Nenhum workflow remoto foi iniciado durante a depuração local desta etapa.
