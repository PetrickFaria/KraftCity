# KraftCity — etapa 02: postos, contratação e vínculos

## Continuidade e escopo

Base aprovada: `feature/kraftcity-foundation`, código
`7ae2ad9fa13102abe581bacd2dd4fe1d025a0f2f`, versão 1.0.95,
[CI 43993573824](https://github.com/PetrickFaria/KraftCity/actions/runs/43993573824).
A inspeção inicial de branches/HEAD/histórico/PRs/workflows/artefatos confirmou
que o único commit posterior era `d0ec9cf`, exclusivamente documentação.
`main` permanecia em 1.0.23 e não foi usada. Desenvolvimento desta etapa em
`feature/kraftcity-employment`, a partir desse estado consistente.

Esta etapa implementa os postos e contratos, não a produção das profissões.
Industrial oferece Fazendeiro, Açougueiro, Pastoreiro, Pescador, Criador e
Apicultor. Comercial oferece Padeiro, Confeiteiro e Churrasqueiro, com cobrança
**desabilitada por padrão**, pois seu preço definitivo continua pendente.
Militar e Construtor não foram implementados.

## Uso e decisões explícitas

Os dois blocos genéricos estão na aba **KraftCity** do Criativo, além dos itens
anteriores. Receitas dos novos blocos não foram inventadas: seus ingredientes
e custos de fabricação não constam da especificação e ficam pendentes.
As receitas residenciais anteriores foram mantidas.

Na tela genérica, a lista à esquerda contém residentes adultos, vivos,
carregados, da mesma dimensão, com profissão vanilla `NONE` e sem emprego
KraftCity ativo. Bebês, nitwits, não residentes e empregados vanilla não são
elegíveis. A paginação cobre a lista; nomes são acompanhados por parte do
UUID, e a seleção enviada ao servidor usa o UUID completo. Chunks não são
carregados à força para procurar aldeões. Registros passivos legados que
contenham inventário próprio não são sobrescritos por contratação automática.

Selecione o morador e a profissão, deposite esmeraldas no slot exclusivo e
clique **Contratar**. A contratação industrial custa exatamente **10**
esmeraldas, inclusive a recontratação. A escolha transforma fisicamente o
bloco genérico em um bloco registrado da profissão; não é uma troca de título.
Cada posto aceita um trabalhador, e cada trabalhador aceita um posto.

Os postos são comunitários nesta etapa: não foi especificado sistema de dono,
equipe ou permissão por residência. A interação exige proximidade normal de
8 blocos do bloco, ou do trabalhador quando aberto por ele, e a mesma
dimensão. Não foi criada regra de alcance da bancada em relação à moradia.
O pagamento é o conteúdo compartilhado do posto, não um débito oculto da
carteira de quem clicou. Esmeraldas excedentes podem ser retiradas e ficam
preservadas ao fechar/quebrar/recolocar.

### Comercial: preço pendente

Configuração de servidor por mundo:
`serverconfig/kraftcity-employment-server.toml`.

```toml
[contratacao]
habilitarContratacaoComercial = false
custoComercialEsmeraldas = -1
```

`-1` significa **não definido**, nunca contratação gratuita. A tela informa a
pendência, desabilita Contratar e o servidor recusa também pedidos forjados,
sem consumir esmeraldas. Apenas uma decisão explícita do administrador pode
habilitar a opção e definir um valor de 0 a 64 (limite técnico do slot único).
Isso é uma configuração opt-in, não uma definição de preço definitivo do mod.

## Inventários e objeto recuperável

| Slots do registro | Uso atual |
| --- | --- |
| 0–26 | Inventário comum 3×9 |
| 27 | Ferramenta |
| 28–29 | Insumos |
| 30 | Equipamento |
| 31 | Exclusivo para esmeraldas |

Os quatro slots específicos são armazenamento reservado para as próximas
etapas. Não consomem insumos, não desgastam ferramentas, não produzem itens
e não aplicam filtros específicos por profissão ainda. A tela informa que a
produção está indisponível. Não há regras de ganho de XP ou subida de nível.

`EmploymentData` (`data/kraftcity_employment.dat`, schema 1, overworld) é a
**única autoridade** dos 32 slots e da associação do posto. Cada estação tem
um UUID, setor, profissão, dimensão/posição, estado ativo/portátil e até um
UUID de trabalhador. Cada ItemStack é serializado uma vez, com índice do slot.
O BlockEntity salva somente a identidade da estação. Os dois caminhos de
abertura — bloco e trabalhador — ligam menus ao mesmo Container do servidor;
clientes recebem os slots pelo protocolo normal de contêineres.

Ao quebrar o bloco, inclusive em Criativo, o vínculo de emprego é encerrado e
um único objeto recuperável contém `KraftCityStation` (UUID). Ele **não**
contém uma cópia dos itens/`BlockEntityTag`. As loot tables desses blocos são
vazias para impedir um segundo drop. O objeto recuperável é protegido contra
dano e não expira; a colocação valida e ativa sua identidade no servidor.
Mesmo cópias criativas do token não podem ativar duas instâncias do inventário.

Depois de recolocar um bloco profissional, seu inventário continua acessível
mesmo sem trabalhador. O botão Contratar permite contratar explicitamente um
substituto da profissão daquele bloco; não há emprego restaurado gratuitamente.
Pistões não movem esses blocos, evitando movimentação sem transferência válida
da identidade. Troca genérico→profissional preserva a mesma estação e não
gera drops. Explosões/remissões normais usam a mesma remoção idempotente.

A reconciliação consulta apenas chunks carregados. Um BlockEntity do tipo
correto que perdeu somente sua identidade pode ser religado ao registro
canônico da posição; um registro ativo cujo bloco desapareceu é convertido
uma única vez em objeto recuperável, mantendo o inventário. Chunks
descarregados não são interpretados como blocos destruídos.

**Backup/transporte entre mundos:** é necessário preservar também o arquivo
SavedData do mundo. O token sozinho não exporta o inventário para outro mundo;
identidades desconhecidas são recusadas em vez de criar conteúdo duplicado.
Objetos perdidos no vazio/remoção administrativa ainda deixam os dados no
registro portátil; recuperação administrativa exige preservar essa identidade.
Não foi criado expurgo automático desses registros nem sistema de propriedade.

## Vínculos, progressão e residencial

`WorkforceData` mantém schema 1 compatível, acrescentando opcionalmente
`profession_history`, com nível/XP por UUID e identificador de profissão.
`setProgress` é uma API de armazenamento explícita para etapas futuras; não
distribui XP nem define requisitos para evoluir. Os trabalhadores contratados
não guardam outra cópia do inventário: este pertence exclusivamente ao posto.

Perder a moradia, morrer, ser descartado, mudar de dimensão ou perder o posto
encerra a associação operacional. Histórico de cada profissão fica preservado.
Recuperar moradia não recontrata automaticamente. Recontratar na mesma profissão
restaura seu nível/XP. Captura/liberação com UUID novo usa a reconciliação de
identidade residencial já aprovada para transferir o histórico, preservando a
proteção contra cópias; não restaura o emprego. Uma identidade de destino que
já tenha histórico não é sobrescrita por uma mesclagem arbitrária.

Descarregar chunk não é morte: vínculos são mantidos sem forçar carregamento,
e conferidos quando o mundo/entidade volta a estar disponível. Menus antigos
fecham ao perder validade. Mensagens conferem sentido, containerId, UUID da
estação, seleção, elegibilidade, setor, estado do posto, distância e pagamento.
Tudo é decidido sequencialmente na thread do servidor; duplo clique e pedidos
concorrentes encontram a associação já ocupada ou o menu antigo inválido.

O trabalhador permanece vanilla `NONE`: não há mudança de profissão vanilla,
trocas ou tarefas de profissão falsa. O Mixin novo impede somente a aquisição
de `JOB_SITE`/`POTENTIAL_JOB_SITE` por trabalhadores ativos; `HOME` e a IA
residencial continuam intactos. Ao perder emprego, comportamento vanilla
normal fica novamente disponível.

A GUI residencial consulta uma API de indicador cujo provider é registrado pelo
módulo de emprego, evitando dependência da GUI em armazenamento de postos.
A integração residencial limita-se a eventos de remoção/reconciliação de
identidade e ao indicador de trabalho de quem participou do sistema novo.
Para esses trabalhadores, verde exige vínculo com o posto real correto e
moradia válida. Moradores legados mantêm seus indicadores anteriores. Não
foram alterados GUI residencial, camas/POIs, áreas, limites, visuais, nomes,
rotinas noturnas, pagamentos, fragmentos, transporte ou filtros anteriores.
A contratação não remove uma reserva de cama existente; a regra residencial
aprovada de liberar reservas durante o dia continua valendo. A nova bancada
não inventa bônus de fragmentos na fórmula antiga.

## Identificadores novos e preservados

Namespace Forge continua `residenciasaldeoes`; todos os IDs antigos permanecem.
Novos blocos/itens: `industrial`, `commercial`, `work_farmer`, `work_butcher`,
`work_shepherd`, `work_fisherman`, `work_breeder`, `work_beekeeper`, `work_baker`,
`work_confectioner`, `work_grill_cook`. Menu/BlockEntity: `workstation`.
Canal novo: `residenciasaldeoes:employment`, versão 1; canal residencial intacto.
Servidor e clientes precisam da mesma versão do JAR para usar o novo canal.

Os modelos novos usam recursos vanilla e cores por profissão; o acabamento
definitivo dos novos postos não foi especificado. Nenhuma textura/geometria
aprovada do residencial foi redesenhada. Comercial tem telas e estruturas,
com contratação desabilitada por preço pendente; profissões não produzem ainda.

## Validação e retomada

Manter as 12 verificações estáticas e os 50 GameTests anteriores, atualizando
somente as expectativas da aba Criativo (agora seis itens) e módulos presentes
(Residencial, Industrial, Comercial). Os novos testes cobrem seis conversões
industriais reais, elegibilidade, pagamento inválido, preço comercial pendente,
duplo clique, concorrência entre jogadores/postos, perda/recuperação de moradia,
progressão, morte/dimensão, captura, inventário compartilhado, quebra/recolocação,
tokens clonados e reconciliação de registro órfão.

O cliente gráfico clica nos botões reais e envia pedidos de rede, verifica
duplo clique, comparação do mesmo Container aberto pelo trabalhador, itens
recolocados, tentativa comercial forjada sem débito, recontratação e verde
na GUI residencial. Nova JVM verifica inventário, associação, nível/XP,
moradia/cama e o estado comercial pendente. Capturas são inspecionadas.

Com Java 17/Gradle wrapper 7.5.1:
`bash gradlew --no-daemon clean build`, os verificadores do workflow,
`python3 tools/run_gametest_ci.py` e, com Xvfb/OpenGL,
`LIBGL_ALWAYS_SOFTWARE=1 python3 tools/run_visual_ci.py`.
Retomar das branches de funcionalidade e do commit/artefato efetivamente
aprovado, nunca de main desatualizada. Etapas 03/04 devem definir produção,
filtros e progressão sem duplicar o inventário nem trocar a identidade do posto.

### Resultado local antes da publicação

- Compilação com Java 17/Forge 43.5.0 aprovada; 12 verificações estáticas aprovadas.
- **62/62 GameTests obrigatórios aprovados**: 50 herdados e 12 novos.
- Roteiro gráfico completo aprovado com saída zero do runner: botões reais,
  pedidos de rede, contrato industrial e duplo clique, mesma instância de
  inventário via trabalhador, token único e recolocação, pedido comercial
  forjado sem cobrança, recontratação nível 3/XP 57 e indicador residencial verde.
- Reabertura em nova JVM aprovada, incluindo inventários, emprego, histórico,
  moradia/cama e as 20 esmeraldas não cobradas no posto comercial.
- Capturas em 1600×900 das telas industrial, profissional, comercial pendente
  e do residencial empregado inspecionadas; não há sobreposição dos inventários.
- O cenário de cama usa um morador dormindo à noite: a regra antiga de liberar
  reservas durante o dia foi preservada. O jogador simulado dos GameTests usa
  posicionamento direto para exercer a validação real de distância do servidor.
- Nenhum workflow remoto foi iniciado durante a implementação/depuração local.
  A versão de entrega planejada é 1.0.96, um único avanço da 1.0.95.
- Não foram executadas sessões dedicadas com dois clientes humanos nem novas
  combinações EMF/ETF/Fresh Animations. Concorrência foi exercitada com dois
  jogadores simulados no servidor e duplo clique por pacotes de cliente real.

### Entrega validada — 1.0.96

- Código aprovado pelo CI: `0cc687b71516d89a5acbd2927ac73353408f0b9e`,
  branch `feature/kraftcity-employment`. `main` não foi usada nem alterada.
- [Execução 44005876367](https://github.com/PetrickFaria/KraftCity/actions/runs/44005876367)
  concluída com sucesso na primeira execução remota desta etapa: build,
  12 verificadores estáticos, **62/62 GameTests**, cliente gráfico e nova JVM.
- [Artefato kraftcity-1.0.96](https://github.com/PetrickFaria/KraftCity/actions/runs/44005876367/artifacts/14295081138):
  instalar somente `build/libs/kraftcity-1.0.96.jar` do ZIP. A cópia de mesmo
  nome em `artifact-output/` é byte a byte idêntica, não um segundo mod.
- SHA-256 do JAR publicado:
  `61b71f25c85718982ba3dc1d74c09fa4228051f0a76faf8c0895e4d956b9d550`.
- Artefato baixado e conferido: hash/relatório/commit, metadados 1.0.96,
  bytecode Java 17 sem classes de teste, seis marcadores gráficos de emprego,
  três marcadores de recarga e 26 capturas em 1600×900. Telas industrial,
  profissional e comercial pendente inspecionadas no resultado remoto.
- Entrega por artefato do Actions; não foi publicada uma GitHub Release.
  O serviço de upload de releases já havia recusado a autenticação neste
  ambiente. Não foi repetida a tentativa nem aberto outro workflow.

Para a próxima etapa, partir desse commit validado (ou da documentação
posterior desta mesma branch), preservar a identidade do posto e seus
inventários, e consultar as pendências acima antes de definir regras novas.
O mod id continua `residenciasaldeoes`; os novos IDs são `industrial`,
`commercial`, `work_farmer`, `work_butcher`, `work_shepherd`,
`work_fisherman`, `work_breeder`, `work_beekeeper`, `work_baker`,
`work_confectioner`, `work_grill_cook`, além do menu/BlockEntity `workstation`.
IDs residenciais anteriores continuam preservados. Cliente e servidor devem
usar o mesmo JAR 1.0.96, pois esta etapa acrescenta o canal de rede de emprego.
