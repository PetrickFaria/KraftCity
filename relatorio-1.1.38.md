# KraftCity 1.1.38 — Trabalho do Lenhador

## A. Pedidos e evidências

| Requisito | Estado | Implementação e validação |
| --- | --- | --- |
| 1. Coleta física do Lenhador | Concluído | O teste posiciona sementes a 10 blocos. No cliente Forge, a madeira surgiu no chão e entrou no inventário depois da aproximação. |
| 2. Margem horizontal de três blocos | Concluído | O teste apresenta um solo na faixa proibida e outro no primeiro bloco permitido. Cada muda de carvalho escuro é verificada individualmente. |
| 3. Distância de cinco blocos de outras áreas | Parcial | O cálculo mede a distância até o ponto mais próximo dos volumes cadastrados. O teste cobre a borda residencial. Falta validar isoladamente cada tipo de zona. |
| 4. Rachaduras progressivas | Concluído | Observadas em cliente Forge durante o corte de uma bétula. A pausa limpa o efeito e o retorno reenvia o estágio. |
| 5. Coleta universal | Parcial | A coleta física compartilhada abrange os sete trabalhadores industriais. Comerciais e Construtores usam fluxos próprios. Soldados mantêm a coleta condicionada à missão e ao combate. |
| 6. Retomada de árvores parcialmente cortadas | Concluído | O cenário nativo cortou um tronco, pausou à noite e cortou os três restantes após reabrir o mesmo mundo em outra JVM. |
| 7. Prioridade de itens, árvores e mudas | Parcial | A ordem foi implementada. A coleta durante o trabalho foi observada. Falta comprovar os três tipos de tarefa simultaneamente no cliente. |
| 8. Baús exclusivos por vínculo manual | Parcial | O barril vinculado recebeu 10 troncos e um graveto e o vizinho ficou vazio. Após trocar o vínculo pela interface, um baú de carvalho do Quark recebeu três sementes coletadas do chão. Falta validar a matriz completa de capacidade e compatibilidade. |
| 9. Golpe de cima para baixo | Parcial | A sequência no cliente Forge mostra o golpe descendente do Villager. A implementação também abrange Steve. Faltam capturas do golpe específico nesse modelo e em Fresh Animations. |

## B. Comportamento implementado

A coleta física usa as mesmas verificações de visibilidade, distância, rota e capacidade do Fazendeiro. Os itens incompatíveis com a capacidade restante, temporariamente inacessíveis ou reservados para outro jogador permanecem no chão. A transferência ocorre no servidor com verificação da entidade e da estação. Slots equipados e insumos reservados ficam fora das ofertas de descarte.

O Lenhador mantém as áreas selecionáveis de 15 a 55 blocos na horizontal e 15 na vertical, com sete blocos para cima e sete para baixo. As restrições de três e cinco blocos aplicam-se apenas ao plantio, juntamente com os sete blocos de afastamento de obstáculos. Cada célula do quadrado de carvalho escuro passa pelas três condições.

## C. Cobertura em aberto

**Não validado na matriz dedicada** 

- Coleta exatamente a um e cinco blocos, na borda, durante o replantio, em disputa entre dois Lenhadores e com estoque comum lotado.
- Replantio com verificação isolada de cada posição entre zero e dois blocos da borda e de cada tipo de volume protegido.
- Rachaduras simultâneas de dois Lenhadores e captura individual dos machados de madeira, diamante e netherita.
- Retomada após redimensionar o posto, quebrar o machado ou substituir um tronco por uma construção no perfil real.
- Depósito do Lenhador em baú duplo, cheio ou destruído e em outras variantes de armazenamento modificado. O teste de baú cheio no perfil real foi inconclusivo.
- Renderização do golpe descendente especificamente no Steve e com Fresh Animations ativo. O perfil real continha EMF e ETF.

A coleta compartilhada foi integrada às sete profissões industriais. A cobertura de Construtores, Soldados e trabalhadores comerciais permanece parcial.

## D. Correções observadas durante os testes

Os testes antigos assumiam que um corte colocava a madeira imediatamente no inventário. A verificação agora aguarda a caminhada e a coleta. Uma copa baixa formava um teto de folhas que bloqueava os itens. A fixture exerce a decadência vanilla das folhas que já perderam suporte antes de exigir a coleta. A fixture de carvalho de 21 troncos usa piso caminhável sob as ramificações e orçamento de tempo para os cortes e as aproximações físicas. A lógica de navegação recusa descidas sem retorno nas rotas de coleta dos trabalhadores industriais que usam a área estrita.

## E. Testes e persistência

Quatro GameTests locais direcionados aprovados após o último ajuste das fixtures. Os 12 verificadores estáticos locais foram aprovados. No GitHub Actions, 351 GameTests obrigatórios, 12 verificadores estáticos, a suíte gráfica e a reabertura de mundo em outra JVM foram aprovados. A segunda JVM da passagem local excedeu o limite inicial do script e posteriormente terminou as verificações de persistência.

O primeiro corte no cliente Forge usou machado de ferro com 200 ticks. Ao anoitecer, o servidor salvou três troncos pendentes e deixou o item já cortado no mundo. Após fechar o cliente e reabrir a mesma cópia em outra JVM, o Lenhador recolheu esse item e completou os três cortes. O inventário passou de seis para dez troncos. Para montar o cenário, a cópia recebeu um machado de ferro e foram ajustados horário e clima. Folhas que obstruíam a visualização foram removidas após registrar a tarefa. O mundo original foi preservado.

## F. Entrega

Versão 1.1.38. Branch `feature/lumberjack-physical-work`. Commit `2db8425b52a9181da8d24ee7d6b783aac9fca289`. Arquivo `kraftcity-1.1.38.jar`, produzido pelo GitHub Actions no commit informado. A integridade do JAR e a igualdade de suas classes e recursos com os testados no perfil Forge foram verificadas. [GitHub Actions](https://github.com/PetrickFaria/KraftCity/actions/runs/45972425233)

SHA-256 `ec56a941223885c40ca505b6cb3bc1601ea40944bd707cf8f78e36a5a1c7f039`. [Download direto do JAR](https://raw.githubusercontent.com/PetrickFaria/KraftCity/refs/heads/downloads/kraftcity-1.1.38/kraftcity-1.1.38.jar)

[Sequência de golpe e rachaduras](evidencias/golpe-e-rachaduras.jpg) · [Retomada em outra JVM](evidencias/retomada-sequencia.jpg) · [Vínculo manual](evidencias/vinculo-manual.png)
