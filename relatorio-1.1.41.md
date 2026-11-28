# KraftCity 1.1.41

## A. Pedidos realizados

- **Lenhador.** Investigar inventário, estado de trabalho, solo, luz, espaço de crescimento, navegação e margens. Concluir o corte, recolher os itens, procurar outras árvores e, quando puder plantar, caminhar até o local e consumir a muda. Verificar espécies, área mínima e máxima, obstáculos, áreas próximas e reabertura.
- **Soldado.** Recolher fisicamente drops comuns de hostis e itens de origem hostil verificada, inclusive equipamento e itens de mods. Preservar o alcance de Guarnecer, a escolta em Seguir, o combate prioritário e a retomada da patrulha. Distinguir drops de itens largados pelo jogador.
- **Apicultor.** Colocar novas colmeias e ninhos em cercas válidas com a entrada na direção frontal do posto nos quatro pontos cardeais. Preservar dados, abelhas, produção, capacidade, cercas e orientação das colmeias já colocadas.
- **Coletor.** Respeitar a reserva de colmeias, ninhos, favos, mel, flores e recursos profissionais em áreas sobrepostas. Preservar a coleta autorizada em área exclusiva, as reservas agrícolas e florestais e a concorrência entre Coletores.
- **Skins.** Manter a cor dos olhos e mapear as mangas retas pelos pixels da skin. Preservar os modelos Villager e Steve, UVs, braços cruzados, ferramentas, animações, alfa parcial e slider. Conferir seis PNGs em vistas frontal, lateral e traseira, com e sem Fresh Animations, EMF e ETF, e após reabrir.
- **Velocidade.** Comparar caminhada normal e fuga do vanilla com rotas profissionais, inclusive árvore, item, colmeia, baú, residência, obstáculos e retomada após tempestade. Preservar velocidades justificadas em defesa, combate e outras rotas específicas.

## B. Implementado

- `LumberjackWork.java` distingue o próprio posto e a jurisdição de patrulha de obstáculos físicos. As margens de 3 blocos da borda, 5 de outras áreas protegidas e 7 de obstáculos relevantes continuam consideradas. O fluxo de corte completo, recolhimento e espera de 5 segundos foi preservado.
- `MilitaryDropOrigin.java` acompanha drops de hostis com origem verificável independentemente da autoria da morte. A origem conhecida prevalece sobre o reconhecimento genérico. As regras de combate, guarnição e escolta continuam aplicadas.
- `ApiaryHiveStock.java` orienta novas colmeias pela propriedade de direção do posto, exige saída desobstruída e reaproveita o item com seus dados. `ProfessionalLoot.java` usa a mesma classificação de colmeias e ninhos para o Apicultor e para a reserva do Coletor.
- `FreshAnimationsFaceTextureAdapter.java` copia os texels dos olhos do PNG para a região que o Fresh Animations usa quando a skin deixa essa região vazia. `WorkerTextures.java` e `BuilderArmsLayer.java` reaproveitam as faces das mangas da roupa Villager preservando RGBA e opacidade global. O modelo Steve mantém seu próprio caminho de renderização.
- `Employment.java` define 0,50 como referência nas rotas comuns. A mudança é aplicada em `ApiaryWork.java`, `FarmerLogistics.java`, `IndustrialWork.java`, `LumberjackWork.java` e `CommercialWork.java`. Retorno ao posto, fechamento do expediente, manobras do Fazendeiro, combate e fuga mantêm seus ajustes específicos.
- `IndustryGameTests.java`, `MilitaryGameTests.java`, `ResidenceGameTests.java` e `EmploymentVisualCheck.java` exercitam esses comportamentos em Forge. O fluxo `build.yml` dá margem de execução aos 364 testes completos.

## C. Pendente

Das seis skins solicitadas, duas puderam ser recuperadas em bytes da cópia do mundo Teste. As outras quatro só chegaram como imagens na conversa. Faltam os PNGs de A-Lenhador-2 até A-Lenhador-5 e a validação gráfica completa dos seis arquivos nos modelos e combinações de mods solicitados. A comparação em percursos equivalentes cobriu o Coletor e aldeões de referência. O ensaio por profissão registrou tempos em quatro rotas adicionais. Ainda faltam uma comparação em condições equivalentes para todas as profissões e a medição de uma fuga espontânea em vez do alvo com fator de fuga.

## D. Correções adicionais

A primeira redução compartilhada de velocidade interferiu no cenário gráfico de recuperação e salto do Fazendeiro. A aproximação aos cultivos voltou ao multiplicador original. As rotas comuns de outras profissões usam o valor base de caminhada do aldeão vanilla. O fechamento noturno também manteve o passo original após a primeira regressão oficial reprovar a entrega do Pastor. O retorno após tempestade manteve 0,80 para a aproximação ao posto. O passeio ocioso usa 0,55, próximo do alvo de caminhada vanilla de 0,50, e permaneceu como estava. O teste residencial de desobstrução passou a controlar horário, clima e caminho, com a mesma exigência de entregar os 19 fragmentos em múltiplas viagens. O limite de execução do GameTest passou de 20 para 25 minutos após uma execução usar 20 min e 6 s. O replantio na área mínima foi testado com um local que respeita a margem residencial e fica dentro do anel que antes tratava o próprio posto como obstáculo.

## E. Testes

- 364 GameTests obrigatórios aprovados e zero reprovados no GitHub Actions. A execução integral local confirmou o mesmo resultado.
- 12 verificadores estáticos, suíte gráfica Forge e reabertura do mundo em outra JVM aprovados. Uma rodada local anterior atingiu o limite de encerramento. A rodada local do commit final capturou os cenários e confirmou os marcadores de persistência. A aprovação integral vem da execução oficial do commit final. Nas duas execuções oficiais anteriores, 362 de 364 GameTests passaram. Elas apontaram falhas na entrega noturna, no retorno após tempestade e no caso residencial de desobstrução. A rodada focada após os ajustes aprovou quatro casos de transporte e retomada do trabalho.
- 83 GameTests passaram na rodada direcionada, com replantio, estoque apícola, quatro orientações, coleta militar e retorno após noite e tempestade.
- Em uma cópia do mundo Teste com 183 mods, o Lenhador caminhou até um ponto preparado e plantou carvalho. A contagem de plantios foi salva, e a muda permaneceu no mesmo local após reabrir em outra JVM.
- A medição em percursos planos de referência registrou, em 40 ticks, 2,536 blocos para o Coletor em busca de um item e 3,099 blocos para um aldeão com alvo de caminhada a 0,50. Um terceiro aldeão com alvo a 0,75, o fator de fuga do vanilla, percorreu 10,866. As distâncias incluem o tempo de início de cada rotina.
- Em quatro cenários instrumentados em uma cópia local dos GameTests, os deslocamentos horizontais e tempos até o evento foram 1,522 bloco e 74 ticks até o primeiro corte do Lenhador, 2,337 blocos e 61 ticks para colher mel após contornar uma parede, 97,453 blocos e 766 ticks para o Padeiro retirar trigo do baú, e 8,062 blocos e 30 ticks para chegar à cama residencial. Os valores são deslocamentos entre os pontos inicial e final e incluem a espera e a ação de cada cenário.
- A-Apicultor1 e A-Lenhador-1 foram recuperadas do salvamento e verificadas como 64×64. As duas têm pupilas RGB (191, 191, 191). A primeira tinha 180 pixels com alfa intermediário. As duas skins foram examinadas no cliente em um perfil com 183 mods, incluindo Fresh Animations 1.8, EMF e ETF, e novamente em um perfil de 181 mods com esses componentes desabilitados. Os olhos permaneceram cinza e as mangas usaram a paleta dos PNGs nos dois cenários. O SHA-256 das skins recuperadas foi preservado pelo salvamento. O teste gráfico também verifica a preservação de alfa e de faces distintas no mapeamento das mangas.

## F. Entrega

Arquivo `kraftcity-1.1.41.jar` para Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

Branch `fix/professional-field-regressions`. Commit `d5850604e9e6315da782276a0747d82d15fd7482`. [GitHub Actions](https://github.com/PetrickFaria/KraftCity/actions/runs/46110725797)

SHA-256 `3b639373598b229bbfd29d8193d3caa9ba4dec12d351fc5bd245804f4387c3d3`. [Download direto do JAR](https://raw.githubusercontent.com/PetrickFaria/KraftCity/refs/heads/downloads/kraftcity-1.1.41/kraftcity-1.1.41.jar)

## G. Acompanhamento

| Requisito | Status | Evidência |
| --- | --- | --- |
| Lenhador volta a plantar mudas | Concluído | Caminhada e plantio de muda na área mínima em GameTest e na cópia do mundo Teste. A suíte cobre bétula e carvalho-escuro. Um ensaio Forge separado confirmou pinheiro, selva, acácia e mangue, com consumo de uma muda em cada ciclo. |
| Soldado coleta drops no modo Patrulhar | Concluído | GameTest de caminhada para equipamento legítimo de esqueleto sem autoria do Soldado, com durabilidade preservada. |
| Soldado respeita alcance em Guarnecer | Concluído | Cenários de coleta, retorno e itens fora do alcance da suíte militar. |
| Apicultor orienta colmeias para frente | Concluído | Quatro pontos cardeais e preservação de ninho ocupado. |
| Prioridade de itens apícolas | Concluído | GameTest com coleta efetiva pelos dois trabalhadores somente nos locais permitidos. |
| Cores dos olhos | Parcial | Mapeamento de pupilas e brancos usa os texels originais. A matriz íntegra de skins e mods continua aberta. |
| Texturas dos braços retos | Parcial | UV por face e alfa preservados. Faltam quatro skins e a matriz completa de modelos e mods. |
| Velocidade investigada | Parcial | Comparação pareada entre o Coletor e aldeões de referência e medição de quatro outras rotas. |
| Velocidade corrigida quando necessário | Parcial | Rotas profissionais verificadas usam 0,50. A rota agrícola de salto e as de segurança mantêm seus ajustes. |
| Persistência validada | Concluído | Segunda JVM da suíte oficial e dados de plantio no mundo copiado. |
