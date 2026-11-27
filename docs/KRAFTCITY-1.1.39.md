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

A lista de contratação industrial foi ajustada para acomodar a nova profissão acima do botão de contratação. Os testes que usavam recursos genéricos para verificar rotas mantêm as mesmas verificações físicas e agora usam itens relacionados à profissão. Os testes de corte medem os drops no chão enquanto a árvore está em andamento.

## E. Testes

Este documento acompanha o resultado oficial do GitHub Actions. A execução de entrega registra a quantidade de GameTests, os verificadores estáticos, o cliente Forge e a segunda JVM. A captura do Coletor faz parte da suíte gráfica e mostra seu menu profissional após uma entrega física.

## F. Entrega

O artefato esperado é `kraftcity-1.1.39.jar`, para Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. A referência do código, o link direto e o SHA-256 devem acompanhar o resultado da validação.

## G. Evidência por requisito

| Requisito | Status | Evidência |
| --- | --- | --- |
| Lenhador conclui a árvore antes da coleta | Parcial | O cenário inspeciona o primeiro drop e o último tronco. |
| Folhas removidas ao concluir | Parcial | O cenário inclui copas em contato e folhas persistentes. |
| Espera de cinco segundos | Parcial | O cenário mede a espera no servidor. |
| Padeiro busca trigo a até 100 blocos | Concluído | Teste direcionado de caminhada até o barril a 100 blocos, retirada e volta ao posto. O barril a 101 blocos fica intacto. |
| Coletor | Parcial | O teste de sobreposição aprovado usa a IA completa. Validação visual na suíte de entrega. |
| Coleta exclusiva por profissão | Parcial | Testes direcionados com trigo, madeira, favo, peixe e drops associados ao Soldado. |
| Áreas sobrepostas | Concluído | Cenoura reservada no cultivo, cenoura disponível fora do cultivo e coleta de couro e peixe na sobreposição. |
| Drops de mobs de mods | Não validado | O teste do evento Forge fornece um drop não classificado por tipo e verifica seus metadados. A matriz com mods reais exige um perfil compatível. |
| Ausência de duplicação | Parcial | Conferências por entidade, quantidade e metadados nos cenários direcionados. |
| Persistência após reabertura | Parcial | A suíte de segunda JVM inclui o vínculo e o depósito do Coletor e a cota do Padeiro. |
