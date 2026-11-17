# KraftCity — Etapa 05: sistema militar

## Continuidade

Auditoria antes de editar: base validada 1.0.98,
`3ea9d2a8548e27a9bf1e8f17aed3de3b8a19dad8`, Actions
[44134517115](https://github.com/PetrickFaria/KraftCity/actions/runs/44134517115),
artefato 14351468768 disponível. Único descendente: `eec547f`, documentação.
PRs antigos 1–4 não constituíam base mais avançada; `main` continuava 1.0.23.
Branch desta etapa: `feature/kraftcity-military`, criada de `eec547f`.

O complemento tem precedência: Guarda substitui Manter; Seguir só autoriza
criaturas realmente atingidas pelo jogador associado; níveis são acumulados
em 15/30/45/60 zumbis, substituindo as metas incrementais antigas.
Militar é funcional; Construtor continua desabilitado. Nenhuma arquitetura
industrial/comercial foi substituída.

## Contratação e inventário

Bloco Militar no Criativo KraftCity, residentes adultos desempregados à
esquerda, seleção Soldado e botão **Treinar**. Custa **15 esmeraldas**, validado
na thread do servidor. Uma contratação por posto/aldeão, com substituição
física pelo Bloco de Soldado. Frente do bloco definida pela colocação e
preservada na contratação. GUI do bloco e do soldado usa o mesmo Container.

`kraftcity_employment` permanece **schema 1**. Os 32 slots existentes mantêm
índices e conteúdo; o militar acrescenta um Container de seis slots,
serializado em `military_equipment`, e metadados opcionais `military`.

| Slots do posto | Uso |
| --- | --- |
| 0–26 | Inventário comum 3×9, incluindo flechas |
| 27–30 | Reservas herdadas, acessíveis e preservadas |
| 31 | Apenas esmeraldas |
| Equipamento 0/1 | Espada/arco/besta; escudo |
| Equipamento 2–5 | Cabeça, peito, pernas, pés |

O SavedData do posto é a autoridade. O equipamento visível na entidade usa as
mesmas pilhas; ao carregar, elas são reassociadas ao ledger antes de operar.
Não há cópia de equipamento dentro do snapshot de transformação. Tokens de
quebra continuam contendo **somente UUID do posto**, nunca cópias dos itens.
Quebrar/desvincular remove o emprego e o equipamento da entidade; os itens
continuam no posto recuperável. Recolocar não recontrata automaticamente.
Histórico anterior é recuperado por uma nova contratação válida. Dados 1.0.98
carregam sem renomear; versões anteriores não conhecem o novo setor/entidade
militar e não são destino de downgrade para mundos que já os utilizem.

Receita de fabricação do bloco genérico não recebeu ingredientes do usuário;
acesso Criativo/comandos segue a decisão documentada nas etapas anteriores.
Nenhuma receita arbitrária foi inventada.

## Modos e alvos

- **Livre**: navegação dentro de 15 blocos do posto, retorno quando fora;
  sai para enfrentar alvos autorizados na cidade e retoma a patrulha.
- **Guarda**: sobe ao posto, para e olha para sua frente; enfrenta alvos
  autorizados e retorna ao mesmo posto. Valores internos legados KEEP/MANTER
  são lidos como Guarda; não há botão Manter.
- **Seguir**: jogador associado ao comando; acompanha sem teleporte e sem
  autorizar resgate da cidade ou autodefesa automática. Só alvos efetivamente
  atingidos pelo jogador, selecionados na Blacklist e a até 10 blocos dele.
  Mantém mais de uma ordem recente, sem alternar foco por tick. Seguir e a GUI
  do trabalhador usam o SavedData mesmo longe do chunk do posto; não o carregam
  à força para consultar o inventário.

Livre/Guarda: ameaça selecionada a até 10 blocos de um residente vivo ou
até cinco além dos limites residenciais, industriais ou comerciais válidos.
Não basta estar perto do soldado. Não usa área de posto removido ou chunk
inacessível. Autodefesa usa o agressor recente, ainda sujeito à seleção.
Resgate da área protegida prevalece sobre agressor secundário; alvo válido
mantém foco até sair das condições, morrer ou surgir essa prioridade.
Jogadores e aldeões residentes não são alvos automáticos.

Blacklist: abas Pacíficos/Neutros/Hostis, filtro Todas/Minecraft/Mods, busca e
rolagem; IDs de tipos registrados permitem selecionar mobs de mods. Textos
são traduzidos no cliente. Preferências persistem por posto. Creeper, neutros,
pacíficos e mobs de mods começam desmarcados; hostis vanilla começam marcados.
Selecionar vaca autoriza ataque mesmo passiva, nas áreas/modos apropriados.
A seleção é respeitada também na autodefesa e no impacto de projéteis.
Não foi copiado código do Minemon.

## Combate e progressão

Espadas usam ataque do Mob, atributos e encantamentos vanilla; golpes gastam
um ponto de durabilidade, sujeitos a Inquebrável. Após golpe, recua cerca de
três blocos e retoma, com intervalo de ataque de 20 ticks. Arma quebrada deixa
socos; equipar nova arma válida a reassocia ao soldado.

Arco/besta procuram aproximadamente cinco blocos, com linha de visão,
projéteis reais, colisão e navegação. Flechas vêm dos slots comuns/reservas;
sem munição não há disparo grátis. Infinidade exige flecha normal presente;
Power/Punch/Flame e encantamentos aplicáveis continuam no projétil.
Besta trata Carga Rápida, Perfuração e Tiro Múltiplo: um consumo de munição,
três projéteis direcionais e durabilidade por projétil, como vanilla.
Flechas geradas do estoque compartilhado não são recolhíveis para duplicar
munição. Bestas entregues já carregadas usam os projéteis salvos uma única vez
e preservam as regras vanilla de recuperação desses projéteis.
Escudo usa o bloqueio direcional e os cinco ticks iniciais vanilla, com
custo de 1 + piso(dano) para impactos de pelo menos 3. Armaduras e capacete
recebem os custos vanilla; maldições/encantamentos permanecem nos itens.

XP usa **milésimos de ponto** no histórico da profissão, preservando 10% e
50% sem arredondar cada morte para zero. As demais profissões não mudam de
unidade. Um zumbi vanilla básico vale 5 XP; limiares militares:

| Nível | XP acumulado | Zumbis equivalentes | Vida máxima |
| --- | --- | --- | --- |
| 1 | 0 | 0 | 5 corações |
| 2 | 75 | 15 | 6 corações |
| 3 | 150 | 30 | 7 corações |
| 4 | 225 | 45 | 8 corações |
| 5 | 300 | 60 | 9 corações |

Usa recompensa real `getExperienceReward` de cada mob; respeita `doMobLoot`. Golpe final de soldado:
100% para ele, 10% para cada outro participante. Golpe final do jogador:
50% para o último soldado, 10% para anteriores. Só dano efetivo registra
participação, persistida no alvo com tick absoluto. Ambiente sem golpe final
atribuído não inventa crédito. Transformação não zera histórico.

## Turnos e transformação

Diurno trabalha fora da faixa 13000–23000; Noturno trabalha nessa faixa.
Fora do turno, procura a cama da residência, reserva pelo mesmo mapa de POIs
e dorme. Não combate nem acorda para socorrer durante o período de sono.
Ao mudar para o turno ativo, acorda. Usa cura normal configurável; dormindo,
o intervalo cai pela metade. Soldados não disputam as rotas civis de entrega
e fechamento de portas; os civis conservam integralmente seus caminhos.

Morte de soldado causada por zumbi é interceptada antes da limpeza civil.
Surge um **Soldado Zumbi** hostil com o mesmo UUID, nome, residência/cama,
posto, equipamentos, XP, vida máxima e dano-base. Usa IA e vulnerabilidade ao
sol vanilla. Não há segundo registro de trabalhador ou pilha de equipamento.
Fraqueza + Maçã Dourada iniciam o processo vanilla de conversão e consomem a
maçã; o fim da contagem restaura o aldeão no mesmo UUID, com os dados originais.
A cura não contrata outro aldeão nem concede XP grátis.

Morrer transformado é definitivo. Equipamentos saem da autoridade do posto
uma única vez, respeitando `doMobLoot` e Maldição do Desaparecimento; o vínculo
residencial/emprego é removido. Inventário comum continua pertencendo ao posto.
Remoção sem morte (descarte/despawn) encerra o contrato e conserva os itens
no posto; unload normal mantém identidade, equipamentos e contagem de cura.
Se perder posto/moradia durante a transformação, a identidade da transformação
fica suspensa no ledger; não permite outra contratação enquanto não resolver
cura/morte. O zumbi deixa de portar cópias; curar não recupera automaticamente
um emprego que foi rompido. Itens e histórico ficam disponíveis para recuperação.

## Decisões de implementação configuráveis

`world/serverconfig/kraftcity-military-server.toml`:

- `curaNormalTicks = 200`: provisório, 1 ponto de vida por intervalo; sono 100.
- `contribuicaoTicks = 1200`: provisório, expiração de contribuição/ordem.
- `deteccaoPosto = 48`: limite operacional provisório de busca/perseguição;
  não amplia os limites aprovados 10 (residente/jogador), 5 (perímetro), 15 (patrulha).

Dano-base 2 e tempos de combate seguem o Mob/armas usados na implementação;
não foram aprovadas bonificações de dano por nível, então nenhuma foi criada.
XP por abates enquanto transformado não é concedido para ataques hostis aos
civis. Esses detalhes são escolhas explícitas desta implementação, não novas
decisões definitivas do usuário. Cliente/servidor devem usar o mesmo JAR;
canal emprego v4 e novo canal militar v1, com comandos validados no servidor.

## Matriz de regressão e entrega

Mantidos os 117 GameTests anteriores. A expectativa da fundação passa de três
para quatro módulos funcionais e de seis para sete itens do Criativo; nenhuma
asserção funcional civil foi removida. Testes militares cobrem contratação,
alvos, filtros, modos, armas, munição, escudo/armadura, níveis, XP distribuído,
cura vanilla, morte definitiva, turnos/cama, NBT e quebra do posto.
Testes gráficos acrescentam menus reais, seleção por pacote, combate físico,
retorno à Guarda, transformação/cura e reabertura em outra JVM.

Resultado local: **152/152 GameTests** (117 anteriores + 35 militares),
**12/12 verificadores estáticos** e cliente gráfico completo aprovado, com
42 capturas e reabertura do mundo. Commit, Actions, artefato e SHA-256 serão
registrados após a validação remota.

## Identificadores e evidência local

Adicionados `residenciasaldeoes:military`, `residenciasaldeoes:work_soldier`,
`residenciasaldeoes:soldier_zombie`, menu `residenciasaldeoes:soldier` e profissão
lógica `kraftcity:soldier`. Nenhum ID anterior foi renomeado. A propriedade
horizontal `facing` é aditiva nos postos; estados antigos assumem Norte,
sem modificar inventário/identidade ou os visuais civis já aprovados.

Validação gráfica local completa: 42 PNGs de 1600×900, menus inspecionados,
três cadeias comerciais, seis profissões industriais, combate/retorno/Blacklist
militares e cura. Nova JVM confirmou os seis grupos de persistência
(residência, workforce, emprego, indústria, comércio, militar) e a operação
comercial parcial retomada uma vez. Os logs finais não apresentam exceção de
tarefa ou erro não tratado. Os 12 verificadores estáticos passaram.

Não houve sessão adicional com dois clientes humanos, nem teste exaustivo
com mods de criaturas/EMF/ETF/Fresh Animations. Compartilhamento/concorrência
foram automatizados no servidor; IDs de mods usam a mesma seleção persistente.
O runner continua aceitando apenas o encerramento tardio conhecido do daemon
Gradle depois de todos os marcadores obrigatórios reais do Minecraft.
