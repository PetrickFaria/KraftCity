# KraftCity — etapa 01: fundação e subsistema residencial

## Base auditada antes da implementação

O checkout inicial estava em `4026e6e10c0c8699293f28ea33f3c51c81a72ed7`
(`main`, versão 1.0.23). A inspeção de branches, histórico, PRs, workflows e
artefatos encontrou a linha mais avançada em `feature/resident-skin-opacity`:
`e2ac8d64351cdaa811472317adb0a6e0d8d5e973`, versão 1.0.94, descendente de main.
Essa é a base desta etapa, na branch `feature/kraftcity-foundation`.

- CI da base: [39072246150](https://github.com/PetrickFaria/KraftCity/actions/runs/39072246150), concluído com sucesso.
- Artefato da base: `residencias-dos-aldeoes-1.0.94`, ID `12115991390`.
- PRs abertos #1–#4 referem-se a versões 1.0.38–1.0.44; não foram mesclados
  nem usados para substituir a linha 1.0.94. Não havia release publicada na inspeção.
- Todos os demais heads são ancestrais da base, exceto `visual/anchor-redesign-1.0.29`,
  uma linha visual divergente 1.0.30. Ela não foi sobreposta à linha posterior validada.
- A suíte atual possui 44 GameTests residenciais, teste gráfico com reabertura
  do mundo e 12 verificações estáticas. As correções históricas permanecem
  descritas em `CORRECOES-E-LIMITACOES.md`.

## Inventário preservado

O módulo residencial mantém o bloco próprio, GUI/lista de moradores,
personalização PNG/ZIP e modelo Steve, opacidade individual e prévia; vínculos
por UUID/identidade de captura, nomes e notificações; tamanhos 3/5/7/9/11 e
capacidades 1/3/5/7/9; limites e buffers de renderização; camas exclusivas,
memórias/POIs e recuperação em Y negativo; retorno, sono, chuva e portas
noturnas; desvinculação, conversões e morte; produção/estoque de fragmentos,
10 slots de saída, transporte físico, filtros e animação do baú; porta
reforçada, receitas, drops, configuração e proteção contra referências
residenciais/rotas obsoletas. Não foi alterada nenhuma dessas regras.

Os arquivos de lógica residencial, GUI, rede, SavedData residencial, mixins,
modelos, texturas, receitas e loot tables são idênticos aos da base. A antiga
classe de registro perde somente `@Mod` e o respectivo import, recebendo um
comentário explicativo. O bootstrap continua registrando os mesmos objetos,
configuração e handlers, uma única vez pelo novo ponto de entrada.

## Identificadores e apresentação

| Elemento | Decisão |
| --- | --- |
| Nome público, descrição Forge, título do projeto e resource pack | KraftCity |
| Ponto de entrada | `br.com.kraftcity.KraftCity` |
| Nome do JAR/artefato/release | `kraftcity-<versão>` / KraftCity |
| Aba adicional no Criativo | `itemGroup.kraftcity`, traduzida; expõe os quatro itens funcionais existentes |
| Abas criativas anteriores | Preservadas; a nova aba não remove o acesso anterior |
| Mod id/namespace Forge | `residenciasaldeoes`, preservado deliberadamente |
| Bloco, item, BE e menu residencial | `residenciasaldeoes:ancora_residencial`, preservados |
| Porta, baú, corpo interno e fragmento | IDs anteriores preservados; corpo interno continua sem item |
| Assets/data, receitas, tags, loot, traduções GUI | Caminhos/chaves anteriores preservados |
| Canal de rede e protocolo | Preservados |
| Dados residenciais | `residenciasaldeoes_residences`, preservado |
| Configuração e skins | `residenciasaldeoes-common.toml`, `config/residenciasaldeoes/skins`, preservados |
| Classes residenciais | Pacote legado preservado como subsistema, evitando refatoração de comportamento |
| Dados novos | `kraftcity_workforce`, schema 1, separados e criados sob demanda |

Não há migração de namespace nem necessidade introduzida de converter saves.
Mundos novos são aceitos, mas não são exigidos por esta alteração. Não instale
o JAR antigo e o KraftCity juntos: ambos declaram o mesmo mod id.
Uma futura mudança para mod id `kraftcity` exigirá tratar todos os registros,
recursos, dependências e dados antigos; isso não foi antecipado nesta etapa.

## Arquitetura e decisões abertas

`KraftCity → ResidentialModule → registrador residencial legado` é a única
ligação de bootstrap. `core` e `workforce` não importam GUI nem dados
residenciais. `CityModule` reserva identidades `kraftcity:residential`,
`industrial`, `commercial`, `military` e `builder`; apenas residencial está
implementado. Essas identidades não registram blocos, itens ou profissões.

`WorkforceData.get(ServerLevel)` usa SavedData no overworld, compartilhado
entre dimensões e acessado na thread do servidor. `WorkerRecord` guarda UUID,
profissão opcional por ResourceLocation, nível inteiro não negativo,
posto opcional (tipo, dimensão, posição), área opcional (dimensão e dois
limites inclusivos) e inventário com cópias defensivas dos ItemStacks.
Mutação usa `put/remove`, marca os dados como dirty e não altera residências.
IDs de profissões/postos desconhecidos são preservados sem ativar sistemas.
Schema desconhecido, UUID duplicado, nível negativo ou limites invertidos
são erros explícitos, evitando sobrescrita silenciosa.

Nível zero não tem efeito de jogo; tamanho de inventário, progressão, salários,
profissões, alcance/posse de áreas, exclusividade de postos e interação entre
residência e trabalho dependem de especificação futura. Não há atribuição
automática, sincronização de cliente, tick, IA, receita ou GUI de trabalho.
O formato de caixa é apenas uma representação inicial de dados, sem impor
as regras de área residencial aos futuros módulos.

## Matriz de regressão e evidências

| Área | Verificação |
| --- | --- |
| Compilação/registro Forge | `clean build`; metadados, entrada KraftCity, itens, receitas e IDs em GameTest |
| Colocação/remoção e referência fantasma | Novo `residential_place_remove_releases_links_beds_and_saved_home`: BE real, vínculos, remoção, liberação de camas e ausência de Home |
| Tamanhos/ocupação | Mesmo teste percorre 3/5/7/9/11, capacidades, rejeita 4 e redução superlotada |
| Camas/POIs | Suíte herdada: exclusividade, POI obsoleto, Y negativo, três moradores em três camas |
| Noite/chuva/portas | Suíte herdada: retorno à mesma cama, sono, um líder, caminhada sem cama e portas fechadas |
| Persistência | NBT residencial herdado; cliente reabre o mundo em nova JVM e verifica dez pilhas e um WorkerRecord; novos testes de SavedData de trabalho |
| GUI/renderização | Cliente gráfico herdado captura painel, portas, baús, transporte e opacidade em várias porcentagens; inspeção de imagens ainda é necessária |
| Limites visuais/cubos fantasmas | Renderizador preservado; nova sequência gráfica ativa duas áreas via GUI/rede, remove uma e desliga a outra, com inspeção dos snapshots do cliente e três capturas |
| Receitas/nomes/Criativo | Verificadores herdados e novo GameTest de receitas, itens e nome público |
| Fundação passiva | Round-trip, dimensões distintas, inventário defensivo, dirty/removal, schema inválido, duplicatas e isolamento dos dados residenciais |

Resultados da execução desta etapa são registrados ao final deste documento.
Não se deve interpretar testes estáticos como validação dentro do jogo nem
ausência de alteração como comprovação de toda combinação de mods externos.

## Resultado local e preparação da entrega

- `clean build`: aprovado com Java 17, Forge 43.5.0 e Gradle 7.5.1.
- As 12 verificações estáticas do workflow: aprovadas, incluindo geometria,
  texturas, receitas, UI, identidade de captura e rotina noturna.
- Servidor Forge real: **50 testes concluídos, 50 obrigatórios aprovados**
  (44 residenciais herdados + 6 de fundação/regressão). O runner também
  confere se a quantidade executada corresponde ao source set selecionado.
- Cliente gráfico real: entrega de 17 fragmentos sem drops, abertura/fechamento
  de baú e sobreposição com jogador, opacidade individual, GUI residencial e
  sequência de limites aprovadas. Capturas 1600×900 inspecionadas: painel com
  dez slots, porta, baú/transporte, opacidade múltipla e limites 2 → 1 → 0.
- Nova JVM no mesmo mundo: `TEN_SLOTS_WORLD_RESTART_PASSED` e
  `WORKFORCE_WORLD_RESTART_PASSED`; GUI restaurada capturada e inspecionada.
- Foi corrigida apenas a preparação do teste gráfico: itens remanescentes
  dos GameTests/limpeza de contêineres eram contados como drops do transporte.
  A limpeza ocorre antes do transporte; a exigência de zero drops durante a
  entrega permanece. A reabertura reposiciona o jogador dentro do alcance
  normal do menu, pois a cena nova termina longe da âncora original.
- O Gradle reporta encerramento inesperado do daemon após o desligamento
  normal dos processos de jogo; o runner herdado aceita somente esse caso,
  após comprovar marcadores de sucesso e evidências novas. Não é tratado como
  compilação aprovada por código de saída zero.
- A lógica residencial, rede, menus, dados, mixins, recursos e receitas foram
  comparados à base; apenas o bootstrap e a apresentação mudaram.
- Não foram revalidadas combinações externas de EMF/ETF/Fresh Animations ou
  multiplayer autenticado. Não há alegação de cobertura de todas as ações
  manuais possíveis de cada tela.

A versão de entrega é **1.0.95**, mantendo a sequência da 1.0.94. Tags de
publicação não disparam outra compilação idêntica; não se usa o workflow de
release para recompilar o mesmo código. O JAR efetivo e seu SHA-256 são os da
entrega/CI, identificados pelo SHA do commit, não pelos artefatos antigos de main.

A primeira execução remota da etapa (`43989007523`, commit `c5e482b`) compilou
e aprovou 49/50 GameTests, mas falhou na observação do estado transitório
`NIGHT_WANDER` exatamente no tick 850. A porta estava fechada e a tarefa
encerrada; a lógica herdada limpa legitimamente esse estado quando uma
atividade vanilla assume a navegação. O teste passou a observar a retomada
da caminhada ao longo da janela e a exigir, em **todos** os ticks após a
conclusão, que a porta permaneça fechada e a tarefa não reinicie. As regras de
jogo não foram alteradas. Uma execução posterior só é iniciada após essa
correção concreta e sua validação local; não há reexecução remota idêntica.

## Retomar na etapa 02

Partir do HEAD de `feature/kraftcity-foundation` e do SHA/artefato da entrega,
nunca de `main` 1.0.23. Ativar Java 17 e usar Gradle wrapper 7.5.1.
Executar `bash gradlew --no-daemon clean build`, as 12 verificações listadas
no workflow, `python3 tools/run_gametest_ci.py` e, com Xvfb/OpenGL,
`LIBGL_ALWAYS_SOFTWARE=1 python3 tools/run_visual_ci.py`.
Os runners exigem logs/imagens novos da execução atual.

Implementar somente o módulo/profissão que a próxima especificação pedir.
Usar `br.com.kraftcity.workforce` para os contratos persistentes, mantendo
registros e lógica do residencial separados. Não habilitar os quatro setores
planejados apenas para preencher uma GUI. Preservar IDs e a matriz acima.
