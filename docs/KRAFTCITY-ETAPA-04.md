# KraftCity — Etapa 04: profissões comerciais e cadeia produtiva

## Base e decisões aprovadas

Base de código validada: **1.0.97**,
`0d4b7d4d81568474eec7ef39af2143199d32bb10`,
[CI 44093358652](https://github.com/PetrickFaria/KraftCity/actions/runs/44093358652).
A auditoria de refs/HEAD/histórico/PRs/Actions/artefatos encontrou apenas
`67ae4f4`, documentação posterior. `main` seguia em 1.0.23 e não foi usada.
Desenvolvimento em `feature/kraftcity-commerce`, sem reiniciar a arquitetura.

O complemento do usuário substitui as pendências históricas das Etapas 02/03:
contratação comercial **habilitada, por 10 esmeraldas**, igualmente para
Padeiro, Confeiteiro e Churrasqueiro. Não há pendência de preço ou de XP.
Uma receita concluída conta como uma produção, independentemente do número
de itens do resultado. Critérios e tempos aprovados:

| Nível | Produções acumuladas | Tempo de uma receita |
| --- | --- | --- |
| Lv1 | 0 | 60 s / 1200 ticks |
| Lv2 | 25 | 50 s / 1000 ticks |
| Lv3 | 50 | 40 s / 800 ticks |
| Lv4 | 75 | 30 s / 600 ticks |
| Lv5 | 100 | 15 s / 300 ticks |

Os tempos são ticks ativos do jogo a 20 TPS; caminhar, noite, chuva,
ausência de moradia, falta de insumo/combustível e falta de acesso não são
produção instantânea nem crédito de tempo offline. Sob atraso do servidor,
segundos de relógio podem ser maiores. Histórico/níveis anteriores não são
apagados nem rebaixados.

O usuário também aprovou explicitamente **combustível vanilla**, com consumo
equivalente à receita de forno — por exemplo, **um carvão para oito carnes** —
separado dos tempos comerciais. Isso não é uma regra de combustível pendente.

## Uso e interfaces

Contratar pelo Comercial genérico existente, selecionando morador residente
adulto e desempregado e uma das três profissões. O servidor valida elegibilidade,
posto, pagamento e concorrência. A contratação substitui fisicamente o bloco
pelo registro profissional já existente e mantém um trabalhador por posto.
Industrial continua custando 10; sua lógica de profissões não foi substituída.

Bloco e trabalhador abrem o mesmo Container persistente de 32 slots:

| Índices | Uso |
| --- | --- |
| 0–26 | Inventário comum 3×9: insumos e resultados |
| 27 | Ferramenta/reserva legada, preservada |
| 28 | Entrada opcional de trigo/cana/carne |
| 29 | Reserva legada, preservada |
| 30 | Combustível no Churrasqueiro; reserva nos demais |
| 31 | Exclusivo de esmeraldas, nunca usado como combustível |

Insumos podem ficar no comum ou no slot 28. Combustível pode ficar no comum
ou no 30, com prioridade ao 30. Itens antigos incompatíveis com uma entrada
não são apagados nem reinterpretados como insumo. Nenhum índice mudou.

A interface mostra nível/produções acumuladas, área, tempo da operação,
barra de progresso e estado do trabalho. **Cancelar operação** zera somente
o tempo/receita em andamento; não há materiais escondidos para perder ou
reembolsar. Uma receita já concluída nunca é desfeita por cancelar.
Há controles de área/limites azuis e associação de baú, reutilizando a base
existente. As três linhas do inventário permanecem sem sobreposição.

## Receitas reais, sem receita adicional

- **Padeiro:** receita de fabricação registrada de 3 trigos → 1 pão.
- **Confeiteiro:** receita registrada de cana → açúcar. No vanilla 1.19.2,
  `minecraft:sugar_from_sugar_cane` converte **1 cana em 1 açúcar**.
  A receita de açúcar a partir de frasco de mel não é usada como se fosse cana.
- **Churrasqueiro:** receitas de forno registradas para carne bovina, suína,
  frango, carneiro e coelho. Cada carne crua vira uma assada da mesma espécie,
  na mesma quantidade, uma unidade por operação. Não converte uma carne em outra.

O módulo consulta o RecipeManager do servidor/datapacks e não registra novas
receitas de comida. Se a receita apropriada for removida, a operação pausa;
não há uma receita oculta substituindo a decisão do jogo/modpack. Cache de
receitas é invalidado na sincronização/reload dos datapacks.

Para completar Fazendeiro → Confeiteiro, foi acrescentado o suporte necessário
à cana no Fazendeiro: colher os dois segmentos superiores de uma coluna de
três, deixando a raiz. Cana de uma ou duas alturas não é colhida. Plantio
somente em base válida com água real; não usa a irrigação virtual como fonte
indevida. O Fazendeiro não empilha automaticamente cana sobre outra cana para
fabricar maturidade. O rendimento dessa cultura especial é o vanilla de dois
segmentos, sem aplicar bônus artificial de enxada. Uma colheita concluída
conta uma ação industrial. Culturas/limiares/desgaste anteriores permanecem.
A reserva plantável do Fazendeiro inclui cana até tamanho², descartando o excedente.

## Descartes e transporte

Baú válido nesta etapa: baú vanilla simples/duplo ou de armadilha, ou barril,
carregado e desobstruído, dentro da área ou a até cinco blocos de seu limite.
Baú residencial exclusivo de fragmentos e Ender Chest não são usados como
armazenamento comum. Baú trancado exige acesso válido do trabalhador; a outra
metade de um baú duplo também precisa estar carregada e acessível.

A distância é euclidiana entre o centro do bloco do baú e o prisma de centros
da área já existente; inclui o interior. Um baú exatamente cinco blocos além
de uma face é válido; uma diagonal cuja distância seja maior que cinco não.
Prioridade por distância quadrática ao posto; empate por X, depois Y, depois Z.
Baú duplo é um único Container, independentemente da metade mais próxima.

A associação manual anterior é preservada como metadado; a seleção operacional
agora obedece à regra nova de qualquer baú válido mais próximo, em vez de
exigir vínculo manual exclusivo. O botão de associação aceita o alcance novo.
Não foi criado outro tipo de baú ou receita de baú. Chunks não são solicitados
para procurar ou acessar recipientes; candidatos carregados são cacheados por
até 20 ticks e revalidados no uso. Um baú novo pode levar até um segundo para
entrar no cache; associar manualmente invalida o cache imediatamente.

Padeiro/Confeiteiro buscam insumos em descartes de postos de Fazendeiro;
Churrasqueiro, de Açougueiro. O posto industrial precisa estar ativo e ser o
BlockEntity canônico da identidade. O comerciante escolhe a fonte adequada
mais próxima, caminha até ela e retira **somente a quantidade faltante de uma
receita**, respeitando estoque e espaço. Não há débito remoto enquanto anda.
Ao amanhecer ou ao faltar material durante o dia, pode repetir a viagem;
se já tem insumos suficientes, segue diretamente ao posto. Não se inventou
um lote diário gratuito ou uma cota arbitrária de estoque.

A ordenação entre baús compartilhados também é aplicada à retirada de cinco
insumos dos industriais no amanhecer, mantendo o limite anterior de cinco.
As seis profissões e suas receitas de ferramentas continuam disponíveis.

Ao entardecer (12000), o comerciante leva os produtos concluídos ao descarte
próximo, preservando os insumos e combustível de operações futuras. Baú cheio
ou ausente mantém os produtos no posto, sem drop duplicado ou perda. Depois,
retorna à residência; às 13000, a rotina residencial aprovada assume cama,
portas e sono. Se a entrega não terminar antes disso, os itens ficam no posto
para uma próxima oportunidade; o sistema não vence o sono residencial com
teleporte ou trabalho noturno. Um caminho caminhável até baú/posto é necessário.

## Tempo, combustível e transações

Tarefas residenciais aprovadas, como entrega de fragmentos e portas, mantêm
prioridade sobre o trabalho comercial. O cronômetro pausa e a interface informa
a entrega residencial; um baú de esmeraldas acessível continua necessário para
essa tarefa anterior.

A receita é fixada por ID no início. O tempo só acumula no posto, com vínculo,
insumos e combustível válidos. A falta de um requisito pausa sem apagar o
progresso. Receita diferente não herda o tempo de outra: é necessário concluir
ou cancelar a operação. Nível/XP pertencem ao trabalhador; a operação parcial
e seus itens pertencem ao posto. Recontratação explícita pode continuar o
trabalho parcial; a produção concluída credita XP ao trabalhador que a termina.

Antes de concluir, o servidor simula retirada de todos os ingredientes,
combustível e recipientes remanescentes, e inserção do resultado no mesmo
inventário. Só então efetua o commit. Saída cheia não debita trigo, carne,
carvão, frasco/balde ou XP. Um slot liberado pelo ingrediente pode receber o
resultado. Vínculo/moradia são revalidados após callbacks de proteção para
impedir uma conclusão se um evento remover a residência durante a transação.

Churrasqueiro usa `ForgeHooks.getBurnTime` para os combustíveis de forno e
`getCookingTime` da receita de carne para o débito de calor. Vanilla:
carvão = 1600 unidades, receita = 200, portanto oito receitas. Dois gravetos
podem fornecer juntos as 200 unidades. Balde de lava devolve o balde vazio,
e o espaço desse remanescente é validado junto com a carne. Calor excedente
fica salvo no posto e não é perdido durante pausas; não é gerado por abrir
GUI, reiniciar o mundo ou recolocar um token. Não há queima passiva em segundo
plano nem consumo por velocidade de nível: o custo segue a receita vanilla.

Tudo roda na thread do servidor, visitando até oito postos comerciais por
tick. Crédito de tempo é limitado por ticks efetivos do aldeão e por visita;
unload, troca de trabalhador/posto e reinício não dão tempo offline.
Menus e pacotes não podem escolher custo, resultado ou tempo creditado.
Dois trabalhadores disputando o mesmo baú observam o estado atualizado depois
da primeira retirada. Quebra/recolocação mantém uma única identidade do posto.

## Configuração e atualização de mundos

`world/serverconfig/kraftcity-employment-server.toml`:

```toml
[contratacao]
habilitarContratacaoComercial = true
custoComercialEsmeraldas = 10
regrasComerciais = 1
combustivelChurrasqueiro = "VANILLA_FUEL"
```

Uma migração **única** substitui os antigos defaults `false/-1` por `true/10`
quando `regrasComerciais` era 0/ausente e grava 1. Isso habilita a decisão
aprovada também nos saves existentes, em vez de funcionar só em mundos novos.
Depois disso, alterações administrativas explícitas continuam possíveis e
não são sobrescritas a cada carregamento. O modo `PENDING` continua disponível
como pausa administrativa de combustível, mas a entrega usa o modo aprovado.
Os testes anteriores de preço pendente são mantidos como testes da pausa
administrativa, não como afirmação de que o preço ainda está indefinido.

`kraftcity_employment` continua schema 1. Campo opcional `commerce` guarda
receita, tempo ativo, calor, estado e época do vínculo; o campo `industry`,
32 slots, UUIDs e `kraftcity_workforce` permanecem. Saves sem `commerce` abrem
com estado vazio, sem apagar itens. Nenhum identificador anterior foi renomeado.
O canal de emprego passa a v3; cliente e servidor devem usar o mesmo JAR.

## Validação e retomada

Os 91 GameTests anteriores permanecem, incluindo pausa administrativa,
residências/camas, limites, ferramentas, contratação, captura, transporte,
proteção, inventários e as seis profissões industriais. A matriz nova cobre:

| Área | Evidência |
| --- | --- |
| Contratos | Três blocos reais, custo 10, duplo pedido sem segundo débito, migração uma vez |
| Receitas/níveis | Trigo 3→1, cana 1→1 vanilla, cinco carnes corretas, 100 conclusões e todos os tempos |
| Combustível | Carvão/8, dois gravetos, balde de lava remanescente, falta/retorno de combustível |
| Segurança | Inventário cheio, insumo retirado, pausa noite/chuva, cancelamento, proteção e remoção de moradia por evento |
| Cadeias | Colheita industrial real → descarte → retirada física → produção de pão/açúcar/carne assada |
| Compartilhamento | Dois trabalhadores com estoque escasso, dois menus/reconexão, baú duplo, alcance/desempate |
| Persistência | Operação parcial/calor/NBT, schema anterior, perda/recontratação com nível, quebra/token único |
| Cliente real | Contratação por botão/pacote, caminhada até a fonte, duração Lv1 real e Lv5, GUI sincronizada, reinício e conclusão única da operação parcial |

Executar build Java 17/Forge 43.5.0, os 12 verificadores do workflow,
`python3 tools/run_gametest_ci.py` e, com Xvfb/OpenGL,
`LIBGL_ALWAYS_SOFTWARE=1 python3 tools/run_visual_ci.py`.

O código não altera regras residenciais nem implementa Militar/Construtor.
Os valores industriais provisórios anteriores continuam documentados na Etapa 03.
Receitas dos blocos genéricos continuam sem ingredientes especificados; acesso
pelo Criativo/comandos é preservado. Não há nova pendência de contratação,
progressão ou combustível comercial: foram decididos pelo usuário nesta etapa.
Validação local concluída: **117/117 GameTests** (91 anteriores + 26 novos),
**12/12 verificadores estáticos**, compilação Java 17 e cliente gráfico real.
As três cadeias produziram após os tempos efetivos; uma nova JVM confirmou os
quatro marcadores de persistência anteriores e os dois comerciais, incluindo
retomada da receita parcial com uma única produção. Foram geradas 37 capturas
1600×900; as quatro telas comerciais foram inspecionadas visualmente.
Não houve sessão adicional com dois clientes humanos nem combinação com
EMF/ETF/Fresh Animations; concorrência foi testada automaticamente no servidor.
Os runners só aceitam o encerramento tardio conhecido do daemon Gradle depois
de confirmarem todos os marcadores obrigatórios de sucesso do Minecraft.
A validação remota final e a identidade do JAR constam abaixo.

A primeira execução remota, `44131546970`, aprovou build, 12 verificadores e
117 GameTests, mas interrompeu o teste gráfico residencial: a câmera se
teletransportava antes de completar a segunda viagem de 17 fragmentos e
encerrava a abertura do jogador por distância. A sequência de teste agora
aguarda conservação, abertura simultânea e fechamento, com relógio de
polling independente e timeout limitado. Nenhuma regra residencial foi
alterada nem asserção removida. O cliente completo e a reabertura passaram
localmente após esse ajuste, antes de publicar a correção.

## Entrega validada 1.0.98

- Branch: `feature/kraftcity-commerce`.
- Commit exato validado: `3ea9d2a8548e27a9bf1e8f17aed3de3b8a19dad8`.
- [GitHub Actions 44134517115 — sucesso](https://github.com/PetrickFaria/KraftCity/actions/runs/44134517115).
- [Artefato kraftcity-1.0.98](https://github.com/PetrickFaria/KraftCity/actions/runs/44134517115/artifacts/14351468768).
- JAR dentro do ZIP: `build/libs/kraftcity-1.0.98.jar`, 484295 bytes.
- SHA-256: `7a2de9bad5cf469ec9fed7c23b98da13ecd2bc963e9ff6cf0ae652f23e0618df`.

O artefato foi baixado e verificado: **117/117 GameTests**, **12/12 verificadores**,
cliente gráfico com as seis profissões industriais e as três cadeias comerciais,
seis marcadores de recarga e 37 PNGs válidos de 1600×900. As quatro telas comerciais
remotas foram inspecionadas. Não há exceção de tarefa nem erro não tratado nos logs
finais do cliente. A cópia em `artifact-output/kraftcity-1.0.98.jar` é idêntica ao
JAR em `build/libs`; instalar **apenas uma cópia**, igual no cliente e servidor.
O ZIP também contém fonte completo, relatórios, capturas e SHA256SUMS.

Para retomar: partir deste commit de código ou de seu descendente documental
com `[skip ci]`; auditar novos commits/Actions antes de alterar arquivos.
Não voltar à `main` desatualizada nem refazer emprego/indústria/comércio.
O commit documental desta seção não altera o JAR validado e não inicia novo CI.
