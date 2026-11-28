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

## C. Pendente

A matriz de loot de todos os mods, a desconexão e a morte durante o pagamento em servidor dedicado e a reabertura do par Lenhador–Coletor durante a mesma pausa ainda requerem testes específicos. A validação usou o Forge 43.5.0, cliente local, GitHub Actions e uma cópia do mundo Teste.

## D. Correções adicionais

A área selecionada do Lenhador permanece após a promoção. Na cópia do mundo, a promoção do Lv2 ao Lv3 desbloqueou 35×35×15 e manteve 15×15×15 como a seleção em uso. O nível comprado também passou a ser considerado pelo tempo de construção. Os testes gráficos passaram a aguardar a chegada do menu e dos dados do servidor antes de depositar ou confirmar. As opções de promoção ficaram separadas dos inventários e das coordenadas do baú militar.

## E. Testes

- 360 GameTests obrigatórios aprovados, zero reprovações na execução final. Inclui navegação do Coletor após amanhecer, retorno durante chuva comum, retomada do corte após noite e tempestade, autorização dos drops e devolução do pagamento cancelado.
- 12 verificadores estáticos aprovados.
- Suíte gráfica Forge e reabertura em segunda JVM aprovadas. As verificações de emprego, inventários, equipamentos, limites e histórico passaram. Nenhuma regressão foi observada nos cenários aprovados. Uma execução local da suíte completa excedeu o tempo de sincronização do pagamento do Churrasqueiro. O cenário comercial dedicado e as execuções oficiais completaram essa etapa.
- Em uma cópia do mundo Teste com 183 mods, uma compilação local da 1.1.40 validou a contratação de Lenhador por Shift-click com 25 esmeraldas, cobrança de 20, cinco devolvidas e promoção de Lv2 para Lv3 com dez esmeraldas. Após a promoção, o próprio Lenhador cortou quatro toras de carvalho e acumulou 4 XP, registrados no salvamento junto com o Lv3 e a área selecionada de 15×15×15. A sessão seguinte, iniciada em outra JVM com a compilação local final, mostrou o mesmo nível, a área mantida e as quatro toras no inventário do posto. O Forge também confirmou a preservação de XP parcial em um cenário de Fazendeiro.
- A cópia do mundo Teste permitiu confirmar que um Soldado em Guarnecer saiu do posto, venceu zumbi, esqueleto e aranha, acrescentou carne podre, flecha e linha ao estoque e voltou às coordenadas do posto. Um segundo grupo incluiu Creeper autorizado, cuja pólvora chegou ao estoque. Os alvos de teste foram criados com vida reduzida e IA desligada para estabilizar a posição. GameTests complementaram a cobertura de patrulha, descarte e exclusão de itens em áreas protegidas.

## F. Entrega

Arquivo `kraftcity-1.1.40.jar` para Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

Branch `feature/emerald-employment`. Commit `48780152a2bc65a1c9315d539539cf4cb27fd82e`. [GitHub Actions](https://github.com/PetrickFaria/KraftCity/actions/runs/46099705231)

SHA-256 `5cb07bb7e55fd130140155d87985644b8e4dd33b969ebc2584e2d4e4c99e1be9`. [Download direto do JAR](https://raw.githubusercontent.com/PetrickFaria/KraftCity/refs/heads/downloads/kraftcity-1.1.40/kraftcity-1.1.40.jar)

[Pagamento no cliente Forge](evidencias/visual-industrial-payment.png) · [Configurações militares](evidencias/visual-military-settings.png) · [Limites residenciais desativados](evidencias/visual-kraftcity-limits-off.png)

## G. Acompanhamento

| Requisito | Status | Evidência |
| --- | --- | --- |
| Soldados coletam drops de mobs hostis | Parcial | Coleta física em GameTest e recursos de zumbi, esqueleto e aranha no mundo Teste. Cobertura de mods e de todas as loot tables permanece parcial. |
| Lenhadores retornam após a noite | Concluído | Teste de retomada do tronco pendente no Forge. |
| Lenhadores retornam após tempestades | Concluído | Teste de suspensão durante a tempestade e conclusão do tronco após o clima melhorar. |
| Coletores retornam após a noite | Concluído | Coletor saiu da residência e coletou couro ao amanhecer. |
| Coletores retornam após tempestades | Concluído | Coletor saiu do abrigo e recolheu o item mesmo com chuva comum. |
| Nova interface de contratação | Concluído | Dois menus no cliente Forge e na cópia do mundo. |
| Cinco slots de esmeraldas | Concluído | Captura do menu com os cinco níveis. |
| Shift-click e distribuição sequencial | Concluído | 10 + 10 + 5 no cliente e 10 + 10 + 10 + 10 + 7 em GameTest. |
| Consumo apenas dos slots completos | Concluído | Cobrança de 20 e nível Lv2 após depositar 25. |
| Devolução de esmeraldas parciais | Concluído | Cinco esmeraldas visíveis no inventário após confirmar e devolução em GameTest ao fechar. |
| Contratação direta em nível superior | Concluído | Contratação de Lenhador Lv2 na cópia do mundo. |
| Compra de níveis por dez esmeraldas | Concluído | Promoção de Lv2 a Lv3 no cliente e preservação de XP parcial no Forge. Nove esmeraldas não habilitaram a compra e o botão ficou indisponível no Lv5. |
| Progressão normal preservada | Concluído | A progressão por trabalho e os testes de XP profissionais passaram. |
| Botões Mostrar Limites com brilho por estado | Concluído | Controles apagados e destacados em capturas do mundo Teste. A suíte verifica sincronização de limites residenciais, industriais e militares. |
| Persistência e ausência de duplicação | Parcial | Segunda JVM e conservação nos fluxos exercitados. Morte e desconexão em servidor dedicado ainda precisam de validação. |
