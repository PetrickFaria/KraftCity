# KraftCity 1.1.39

## A. Pedidos realizados

- Concluir o corte de todos os troncos antes da coleta, preservar a tarefa nas pausas e aguardar 100 ticks após terminar a árvore.
- Remover a copa sem suporte, preservar folhas persistentes, construções e árvores vizinhas e usar as tabelas de saque.
- Buscar trigo em descartes vinculados a Fazendeiros a até 100 blocos do posto de Padeiro.
- Adicionar o Coletor industrial e aplicar a prioridade de coleta por profissão e por posição.
- Reconhecer drops militares pela morte do mob, inclusive para entidades e itens fornecidos por mods que usam o evento padrão do Forge.

## B. Implementação

- `LumberjackWork` conserva a tarefa de corte como prioridade após o primeiro golpe. O próximo horário de corte fica persistido no posto. `LumberjackCanopy` registra uma copa limitada no início da tarefa, confere suporte de outras árvores ao terminar e processa as folhas em lote. O saque usa o contexto do trabalhador e respeita a regra de drops de blocos.
- `BakerWork` usa um raio tridimensional de 100 blocos a partir do posto. A busca considera apenas contêineres vinculados, carregados e com trigo disponível. A retirada conserva as cotas de 15 a 75 trigos e ocorre após aproximação física. O estoque do Padeiro continua separado das suas entregas de pão.
- `Profession`, `WorkMenu`, `IndustrialRules` e `IndustrialWork` integram o Coletor às contratações industriais. A área é centralizada no posto, com 15 blocos de altura e opções manuais de largura de 15, 25, 35, 45 e 55. O trabalhador usa 27 slots e deposita somente no recipiente vinculado.
- `ProfessionalLoot` classifica os recursos industriais. Um datapack pode estender a classificação com tags de itens em `data/kraftcity/tags/items/pickup/<profissão>.json`, utilizando o identificador interno em inglês. Os recursos sobrepostos continuam reservados durante o descanso de trabalhadores contratados.
- `MilitaryDropOrigin` identifica o Soldado responsável no evento `LivingDropsEvent`. A origem persiste na entidade de item e é considerada no empilhamento, na coleta militar e nas reservas industriais.

## C. Limites

A copa é preservada quando a origem permanece ambígua, falta um chunk necessário à análise, ou a folhagem exige uma regra de suporte que o trabalho não reconhece. Tarefas de corte já parcialmente concluídas na versão anterior podem manter folhas antigas sem registro de origem.

O Padeiro depende de chunks carregados e de um caminho transitável. Os trabalhadores comerciais e os Construtores continuam usando os mecanismos próprios de abastecimento. O reconhecimento militar abrange drops emitidos pelo evento de morte do Forge. Mods que geram entidades de itens por outro mecanismo precisam de integração própria para fornecer origem confiável.

## D. Correções adicionais

A coleta de madeira junto a um patamar pode usar um salto físico, com proximidade e visada novamente verificadas antes da transferência. A lista de contratação industrial foi ajustada para acomodar a nova profissão acima do botão de contratação. Os testes que usavam recursos genéricos para verificar rotas mantêm as mesmas verificações físicas e agora usam itens relacionados à profissão. Os testes de corte medem os drops no chão enquanto a árvore está em andamento.

## E. Testes

O GitHub Actions aprovou 357 GameTests obrigatórios, os 12 verificadores estáticos, a suíte gráfica Forge e a reabertura do mesmo mundo em uma segunda JVM. A captura do Coletor mostra o menu profissional após entregar três couros ao barril vinculado. A segunda JVM recuperou o vínculo e o conteúdo do barril.

Em uma cópia do mundo Teste com 183 mods, uma compilação anterior da 1.1.39 cortou uma bétula de seis troncos, manteve a madeira no chão durante o corte e retirou a copa no final. As duas árvores adjacentes permaneceram. A sessão também verificou uma Dripstone Spider de Nebulus com os saques normais e uma tentativa de controle com loot table de vaca. O inventário militar salvo passou a conter um ovo do mod, e o cenário de controle gerou carne. Na compilação local do commit final, o Lenhador cortou o primeiro tronco de outra bétula, salvou quatro troncos e 72 posições de folhas e concluiu o trabalho após reabrir a mesma cópia em outra JVM. A coleta em patamares foi validada por GameTest.

Dois GameTests adicionais, executados separadamente, confirmaram a conservação de um item disputado por dois Coletores, a espera por um slot livre e a preservação da madeira do Lenhador, dos favos do Apicultor e dos itens fora da área do Coletor. A matriz de todos os terrenos e contêineres de mods permanece parcialmente coberta.

## F. Entrega

Arquivo `kraftcity-1.1.39.jar` para Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

Branch `feature/collector-professional-loot`. Commit `8d3dfe28de19473fc86c15bf841751cb2bef4144`. [GitHub Actions](https://github.com/PetrickFaria/KraftCity/actions/runs/46056287581)

SHA-256 `39d50ec870b6da5a0c561daa752c0cacb9a0e660d288dbe347b4ef98b3c0dc72`. [Download direto do JAR](https://raw.githubusercontent.com/PetrickFaria/KraftCity/refs/heads/downloads/kraftcity-1.1.39/kraftcity-1.1.39.jar)

[Coletor em cliente Forge](evidencias/coletor.png)

## G. Evidência por requisito

| Requisito | Status | Evidência |
| --- | --- | --- |
| Lenhador conclui a árvore antes da coleta | Concluído | GameTest com drops no chão durante o corte e captura em uma cópia do mundo Teste. |
| Folhas removidas ao concluir | Concluído | GameTest com copas em contato e folhas persistentes, e captura da remoção da copa da bétula na cópia do mundo Teste. |
| Espera de cinco segundos | Concluído | GameTest mede o relógio após o corte e aguarda a próxima seleção. |
| Padeiro busca trigo a até 100 blocos | Concluído | Teste direcionado de caminhada até o barril a 100 blocos, retirada e volta ao posto. O barril a 101 blocos fica intacto. Teste adicional verifica 10, 30, 50, 75, 99, 100 e 101 blocos. |
| Coletor | Concluído | O teste de sobreposição usa a IA completa. A captura no cliente Forge confirma a entrega ao barril manual. |
| Coleta exclusiva por profissão | Concluído | Testes direcionados com trigo, madeira, favo, peixe e loot verificado do Soldado. |
| Áreas sobrepostas | Concluído | Cenoura reservada no cultivo, cenoura disponível fora do cultivo e coleta de couro e peixe na sobreposição. |
| Drops de mobs de mods | Concluído | O Teste com 183 mods registrou o ovo de Dripstone Spider. O GameTest do Forge também verifica a identificação e os metadados de um drop fora das categorias vanilla de combate. |
| Ausência de duplicação | Concluído | Os testes conferem entidade, quantidade e metadados. Dois Coletores e o inventário cheio foram validados em GameTest. Sessão multijogador dedicada não validada. |
| Persistência após reabertura | Concluído | A segunda JVM aprovou o vínculo e o depósito do Coletor e a cota do Padeiro. Na cópia do mundo Teste, o Lenhador cortou os quatro troncos que estavam pendentes e encerrou a tarefa. |
