# KraftCity — Etapa 06: Construtor cooperativo

Minecraft Java 1.19.2 / Forge 43.5.0 / Java 17. Branch: `feature/kraftcity-builder`.

## Continuidade e origem

A inspeção das branches, histórico, PRs, Actions e artefatos encontrou como base validada a Etapa 05: código `674e55e69446a2bfbefed6249113b303658a5150`, versão 1.0.99, Actions `44184138505`. A branch parte do commit posterior de documentação `b53a8992f9091efa202e53e478c8b5c10e0baa48`. A `main` antiga não foi usada. Nenhum sistema residencial, industrial, comercial ou militar foi substituído.

O JAR Create fornecido tem SHA-256 `1c63b46913455e479ba6f214661213694d7d2bc8d9c24bef845be4e337de001b`, implementação 0.5.0.i, Minecraft 1.19.2. A referência de código-fonte correspondente é [Creators-of-Create/Create, 7ed2f71a01fab15bf4cd64c95b3428ec25666510](https://github.com/Creators-of-Create/Create/tree/7ed2f71a01fab15bf4cd64c95b3428ec25666510). Foram examinados `SchematicAndQuillItem`, `SchematicTableTileEntity`, `SchematicTransformation`, `SchematicRenderer`, `SchematicPrinter`, `SchematicannonTileEntity`, `MaterialChecklist` e `ServerSchematicLoader`.

O formato reaproveitado é o StructureTemplate NBT nativo (`size`, `palette`, `blocks`, `entities`, `DataVersion`). Rotação e espelhamento usam as APIs vanilla. O canhão, Flywheel, armazenamento e rede internos do Create não representam trabalhadores residentes nem o estoque global exigido: o adaptador KraftCity é independente, sem dependência binária, classes ou assets copiados do Create. Os sprites de papel foram desenhados para este projeto; modelos dos blocos referenciam recursos vanilla. O aviso MIT exato encontrado no JAR está em [licenses/CREATE-0.5.0.i-MIT.txt](licenses/CREATE-0.5.0.i-MIT.txt).

## Uso

1. Obtenha `residenciasaldeoes:builder`, `empty_blueprint` e `engineer_paper` na aba KraftCity ou por `/give`. Receitas de fabricação não foram especificadas; não foram inventados ingredientes.
2. Contrate um residente adulto desempregado por **10 esmeraldas**, pela GUI de contratação existente. O posto torna-se `work_builder`; continua ligado à moradia e à cama. Quebrar o posto remove o emprego, não o histórico nem o estoque global.
3. Com Papel do Engenheiro, clique nos dois cantos da região, dê um nome e salve. A captura é limitada por tick e exporta `.nbt` em `config/kraftcity/schematics`. Também é possível instalar arquivos nessa pasta e usar **Atualizar**.
4. Todos os postos e trabalhadores abrem o mesmo estoque de materiais 3×9, tinta, carga, papéis, biblioteca e obras. O slot **Taxa** pertence ao posto e serve apenas à contratação; o slot **Esmer.** alimenta a carga global.
5. Selecione um esquema, insira Papel de Esquema Vazio e tinta, e use **Gravar (5 s)**. A saída ainda não tem brilho. **Materiais** abre a lista completa; a lista de faltas da obra é consultada explicitamente, sem varrer grandes esquemas a cada quadro.
6. Use o papel gravado no chão. Ajuste X/Z/altura, gire em 90° e espelhe. Escape permite olhar/caminhar mantendo a projeção; novo clique reabre os controles. **Cancelar** abandona a prévia. **Confirmar posição** salva a transformação no servidor e ativa o brilho, sem iniciar construção.
7. Leve o papel confirmado a qualquer posto, escolha **Preservar** (padrão) ou **Substituir** e use **Construir**. O papel fica vinculado à mesma obra; cópias dessa identidade não iniciam outra.
8. **Pausar/Retomar** conserva tarefas e tempos. **Cancelar obra** libera a equipe e descarta somente trabalho parcial ainda não pago. Retomar uma cancelada usa a mesma identidade e os blocos concluídos; revalida sobreposição e limite de obras.

## Autoridades e serialização

`kraftcity_builder.dat`, SavedData do overworld, schema 1, é a única autoridade global, inclusive entre dimensões. Guarda 32 slots (27 materiais + tinta + esmeraldas + vazio + saída + planta), barras, biblioteca sanitizada, papéis, reserva de impressão e obras. Postos e trabalhadores não contêm cópias desse inventário. Existe estoque mesmo com zero Construtores e todos os postos quebrados.

Emprego, UUID do posto, inventário individual antigo e recuperação do bloco continuam em `kraftcity_employment`, schema 1. Progressão permanece em `WorkforceData`. `BUILDER` foi acrescentado ao final dos enums, preservando as posições anteriores. Não mudou o mod ID `residenciasaldeoes`, IDs residenciais, arquivos salvos anteriores ou schemas dos sistemas anteriores. O protocolo de emprego passa a v5 e o canal construtor começa em v1: cliente e servidor devem usar o mesmo JAR.

O item recuperável do posto continua levando somente `KraftCityStation` UUID, sem materiais globais. A colocação valida a identidade inativa na autoridade existente. Quebra, morte, perda de moradia e mudança de dimensão liberam reservas de trabalho sem fabricar itens ou restaurar emprego automaticamente. Recontratar restaura XP da profissão. Chunks descarregados suspendem trabalho; após 200 ticks sem entidade carregada, a reserva é liberada para permitir substituição, sem apagar emprego ou histórico.

Cada papel referencia UUID de servidor, esquema imutável, autor, emissão, confirmação, dimensão, origem, rotação, espelho e obra. Tags de item falsificadas não substituem esses registros. Papéis são controlados pelo autor nesta etapa; não foi criado protocolo de transferência de propriedade.

Cada obra conserva modo, transformação, autor, bitmap de células concluídas, recibos de pares, contagem realmente colocada, equipe/posições e tarefas/tempo. Não se atribui tempo offline: relógios e voos são transitórios e reiniciam sem creditar a ausência. Persistência refere-se ao salvamento/reabertura normal do Minecraft; não se afirma atomicidade de disco diante de falha física abrupta da máquina.

## Consumo e colocação

- Tinta: 1000 unidades, um saco = 100; um bloco não-ar = uma unidade. Só entra um saco inteiro quando cabe. Quinze sacos deixam barra 1000 e cinco no slot.
- Impressão: reserva papel uma vez, acumula tinta debitada em escrow e só então conta 100 ticks ativos. Projetos acima de 1000 blocos permitem reposição progressiva, incluindo retomada após reload. Saída ocupada suspende conclusão sem duplicar produto.
- Carga: 500 unidades; uma esmeralda = 100 blocos. Ao atingir 400, consome uma esmeralda e volta a 500. Quinze esmeraldas deixam dez no slot e barra cheia. Contratação e carga são débitos separados.
- Material, carga e XP são debitados somente após colocação real aceita, com revalidação após callbacks Forge. Já correto não custa nada nem concede XP. Proteções, material ausente, conflito, falta de carga e pausas não produzem blocos gratuitos.
- Preservar exige ar ou preservação de água ao colocar um bloco alagado; não remove nem plantas substituíveis. Substituir pode trocar blocos comuns autorizados, nunca BlockEntities/inventários existentes nem blocos indestrutíveis. Não faz demolição automática das células de ar do arquivo. Blocos substituídos não geram drops nem reciclagem automática; esta é uma decisão técnica conservadora, pois reciclagem não foi especificada.
- Camas e blocos de duas metades exigem pares coerentes; um item paga o par, cada metade efetivamente colocada paga uma unidade de carga e XP. Metades superiores aguardam a inferior. Suportes são revalidados no mundo.
- Fontes de água/lava exigem balde correspondente e devolvem balde vazio no estoque. Estados alagados exigem água real, salvo água-fonte já presente. Não se perde o recipiente em estoque cheio; a transação é simulada antes e revalidada após eventos.
- Orientação, eixo, metade, rotação e alagamento são respeitados. Propriedades derivadas por vizinhos (conexões, energia, iluminação, formato dinâmico de escada, distância de folhas) são recalculadas pelo jogo e não geram recolocação infinita.
- Células já concluídas não são reconstruídas automaticamente se outro jogador as remover depois. Células pendentes são sempre revalidadas antes de gastar recursos.

## Equipe e logística

Cada obra convoca os seis postos elegíveis mais próximos, na mesma dimensão e até 150 blocos da origem confirmada. A distância e o desempate por UUID são determinísticos. A chegada é física: os primeiros quatro recebem frente/esquerda/direita/fundo transformados; convites excedentes são liberados. Um UUID não participa de duas obras. Falha de acesso gera espera e nova tentativa; não há teleporte nem carregamento forçado de chunks.

Enquanto disponível, patrulha até 25 blocos do próprio posto. A obra só progride de dia, com residente vinculado, posto ativo, acesso e trabalhador realmente em posição. Ao entardecer, cede o controle às rotinas residenciais de retorno, transporte, portas e cama, sem reescrever a IA civil.

Baús simples/duplos e barris são descobertos no sistema de logística existente, com limite específico de **cinco blocos do posto**, permissões e locks, sem acesso a chunks descarregados. O trabalhador caminha até o recipiente e retira apenas material necessário que caiba no estoque global. Trabalhadores livres dentro da equipe podem ajudar a buscar faltas de outra tarefa. A transação ocorre na thread do servidor; não se movimentam cópias client-side.

XP individual por bloco efetivamente colocado: Lv1/2/3/4/5 em 0/50/150/300/500, com 100/80/60/40/20 ticks por bloco. Os braços alternam apenas durante atividade; os voos são efeitos visuais de modelos de bloco, nunca ItemEntities ou itens duplicados.

## Segurança e desempenho

Captura não lê NBT privado de BlockEntities nem entidades. Baús exportam somente o bloco vazio; itens, comandos, proprietários e dados privados não são copiados. Arquivos desconhecidos ou estados inválidos são rejeitados. Blocos de infraestrutura KraftCity (exceto porta reforçada), comandos, spawners, portais, pistão em movimento e similares não são construídos. Cultivos maduros e fluidos em escoamento recebem impedimento explícito em vez de simulação incompleta. Blocos de mods precisam estar registrados; procedimentos especiais que não possam ser representados por item/estado/suporte seguro não são prometidos.

`serverconfig/kraftcity-builder-server.toml`: padrão volume 262144, aresta 128, comprimido 8 MiB, NBT 32 MiB, 256 arquivos, 524288 células totais em biblioteca, oito obras, 256 verificações/capturas por tick. Arquivos e parsing pesado usam executor de I/O; a incorporação ocorre na thread do servidor. Hash canônico por conteúdo evita importar novamente a captura com outro nome/ordem/UUID. Links simbólicos e escapes da pasta são rejeitados. Alterar um arquivo gera nova versão de conteúdo; não altera uma obra que já aponta para uma versão.

Prévia enviada em páginas de até 512 células, com 1024 células globais por tick; o cliente prepara até 256 modelos por quadro, agrupa VBOs em seções e usa frustum culling. Movimento da projeção não reconstrói malhas; rotação/espelho invalidam o cache. Fluidos usam textura vanilla de superfície em geometria visual própria. Projeção não altera o mundo. Indicadores de obra são limitados a 64 blocos e braços/voos a clientes próximos. Caminhos de renderização de sombras Oculus são evitados conservadoramente.

Alertas se destinam ao autor da obra e são limitados a um por obra a cada 300 ticks; GUI mantém o motivo atual (material, carga, acesso, trabalhadores, conflito). O nome da obra acompanha o alerta.

## Validação e entrega

A bateria local ampliada passou nos 190 GameTests (152 anteriores + 38 do construtor), além dos 12 verificadores estáticos. A validação gráfica completa e o empacotamento final estão em andamento; a versão só deve ser considerada entregue após o relatório de CI e hash anexos ao artefato final. O runner exige todos os testes anotados, sem reduzir contagem ou omitir falhas.

Os testes gráficos acrescentam contratação real via pacote, estoque global em postos distantes 50 blocos, captura nativa com baú privado, lista de materiais, impressão, posicionamento/rotação/espelho, brilho confirmado, quatro trabalhadores, voo/indicador e obra parcial persistida. A segunda JVM deve retomar sem consumo ou XP duplicado, comprovar recolha física de um baú necessário e preservar os 117/152 testes históricos e as capturas anteriores. Não houve sessão com dois clientes humanos nem compatibilidade exaustiva com todos os mods; concorrência tem verificação automatizada de duas autoridades de menu sobre o mesmo estoque.

A identificação final do código, Actions, artefato e SHA-256 constará no registro de entrega abaixo e no relatório gerado automaticamente dentro do artefato.

### Matriz de regressão

| Área | Evidência obrigatória |
|---|---|
| Residencial e aparências | 152 regressões históricas, slots, produção, POIs/camas, portas, transporte, vínculo e opacidade; capturas anteriores mantidas |
| Industrial/comercial/militar | Seis profissões industriais, três cadeias comerciais, contratação, combustível, XP, inventários, equipamento, turnos, cura e nova JVM |
| Contratação do Construtor | 10 esmeraldas, Lv1 inicial, bloco físico, um trabalhador e duplo clique sem cobrança dupla |
| Estoque | Mesma autoridade para menus de postos/trabalhador, dois jogadores, dimensões e exemplo 15/15/64 a 50 blocos |
| Impressão | 250 blocos = 250 tinta, exatamente 100 ticks, emissão única; 1500 blocos com reposição e reload |
| Schematics | NBT nativo, estados, rotação/espelho, hash canônico, ar/privacidade, arquivos inválidos, escape e symlink |
| Construção | Colocação física, débito/XP único, já correto gratuito, materiais/carga faltantes, água e pares de cama |
| Proteções | Preservar inclusive plantas, Substituir sem destruir inventários, WorkActionEvent e EntityPlaceEvent, moradia removida no callback |
| Equipe | Seis mais próximos, raio 150, primeiro grupo de quatro em lados distintos, um trabalhador fora de duas obras |
| Persistência | Tarefa parcial/pausa, histórico após recontratação, token sem cópia global, cancelamento/retomada e conflito de sobreposição |
| Logística | Recipiente trancado intacto, estoque cheio/balde, obra descarregada sem forçar chunk; coleta física necessária na prova gráfica |
| Cliente Construtor | Dez imagens: contratação, estoque, outro posto a 50 blocos, captura, materiais, impressão, holograma, equipe, voo e progresso após recarga |

Os tempos finais dos cenários gráficos incluem deslocamento real e retorno de patrulha/cama. O terreno de teste cobre todo o raio de patrulha e remove criaturas hostis remanescentes dos GameTests, sem tornar os Construtores invulneráveis ou modificar o comportamento de produção.
