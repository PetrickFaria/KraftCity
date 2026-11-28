# KraftCity 1.1.40

## A. Pedidos realizados

- Coleta física de drops militares comuns e de mobs com origem registrada pelo Forge.
- Retorno ao posto de Lenhador e Coletor depois do descanso ou abrigo.
- Contratação em duas telas, pagamento sequencial, devolução de fundos e nível inicial.
- Compra de um nível por dez esmeraldas, com histórico e XP preservados.
- Estado visual dos controles de limites residenciais, industriais e militares.

## B. Implementado

`MilitaryDropOrigin` registra a origem de drops hostis mesmo quando a morte veio de outro agente. A tag `kraftcity:military_common_drops` permite reconhecer consumíveis comuns antigos. O evento de descarte do jogador protege seus itens, e o empilhamento respeita os marcadores de origem.

`IndustrialWork` registra a necessidade de retornar ao posto nas pausas e retoma a navegação física durante o expediente. A rotina residencial libera o retorno forçado quando acabam noite e tempestade, respeitando fuga ativa.

`WorkMenu`, `WorkNetwork` e `LevelPaymentMenu` usam a seleção inicial seguida de um menu de pagamento vinculado ao trabalhador e ao posto. O servidor calcula o nível pelo prefixo de slots completos. O saldo permanece no menu até confirmar ou retornar ao jogador. A compra preserva o XP e o nível comprado serve de piso à progressão obtida pelo trabalho.

`LimitsButton` aplica cores de estado separadas do foco e do cursor aos controles de limites existentes. As configurações da prévia continuam seguindo a fonte de estado de cada tela.

## C. Pendente e cobertura

A evidência de cada cenário depende dos resultados do Forge. Combinações com todos os mobs de mods, cada situação de desconexão e todas as escalas de GUI exigem validação dedicada.

## D. Correções adicionais

O tempo de construção passou a respeitar também o nível adquirido pelo trabalhador. As áreas industriais de seleção manual mantêm a largura já selecionada quando o nível sobe.

## E. Testes

A execução oficial inclui GameTests, 12 verificadores estáticos, renderização Forge e reabertura do mundo em outra JVM.

## F. Entrega

Arquivo previsto `kraftcity-1.1.40.jar`. A entrega inclui versão, commit e SHA-256 da compilação aprovada.

## G. Acompanhamento

| Requisito | Status | Evidência |
| --- | --- | --- |
| Soldados coletam drops hostis | Parcial | Testes de coleta física e de exclusão de itens descartados. |
| Lenhadores retornam após noite | Parcial | Suíte de retomada da tarefa. |
| Lenhadores retornam após tempestade | Parcial | Suíte de retomada da tarefa. |
| Coletores retornam após noite | Não validado | Implementação compartilhada de retorno. |
| Coletores retornam após tempestade | Parcial | Cenário dedicado com chuva comum após a tempestade. |
| Nova interface de contratação | Parcial | Suíte gráfica adaptada para a seleção e o pagamento. |
| Cinco slots | Parcial | Cenário 10 + 10 + 5. |
| Shift-click sequencial | Parcial | GameTest de distribuição. |
| Consumo dos slots completos | Parcial | Roteiro de contratação no cliente. |
| Devolução parcial | Parcial | GameTest de cancelamento e roteiro de contratação. |
| Nível inicial superior | Parcial | Roteiro de contratação no Lv2. |
| Compra por dez esmeraldas | Parcial | Roteiro de compra do Lv3. |
| Progressão normal preservada | Parcial | Compra preserva XP e o trabalho continua contribuindo para a curva existente. |
| Botões de limites por estado | Parcial | Cobertura gráfica do botão de limites. |
| Persistência e conservação | Parcial | Suíte de reabertura em outra JVM. |
