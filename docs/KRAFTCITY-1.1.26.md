# KraftCity 1.1.26

Base 1.1.25 na branch feature/kraftcity-builder, commit c48473d3e5718432616e0a984e95bc665fd1d2a6. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Mãos e sinais visuais

O posto continua sendo o inventário de autoridade. A mão espelha uma cópia visual sem possibilidade de drop. A flor tem prioridade durante atração, alimentação e reprodução. Na coleta aparecem o frasco, a tesoura ou a mão livre, nessa ordem. Após a ação, a mão retorna à rotina normal. A animação usa o balanço do braço existente.

A conclusão do vínculo sincroniza o estado visual, reconstruído quando a abelha entra no mundo a partir do registro do posto. Uma camada desenha somente as duas regiões azuis dos olhos sobre a textura padrão de uma abelha calma. Quando um pacote fornece o modelo ou a textura da abelha, partículas brancas discretas substituem a camada. A abelha agressiva mantém a aparência de agressividade do seu renderizador. O servidor envia um coração nas entregas intermediárias e três na entrega que conclui o vínculo, sempre após consumir a flor.

## Coleta e proteção

A 1.1.25 filtrava a retaliação disparada pela coleta. A seleção vanilla de alvos ainda podia voltar a selecionar o próprio trabalhador. A operação legítima marca uma janela curta de proteção antes de atualizar o nível de mel. Durante a janela, a proteção compara as identidades da abelha, do posto e do trabalhador e impede retaliação contra o próprio Apicultor, inclusive ataques e seleção de alvo. A agressividade contra outros alvos, os vínculos de outros postos e a coleta manual seguem as regras do jogo.

## Território e reprodução

A migração leva 5×5 a 7×7 e conserva as seleções de 7×7 a 13×13 já usadas. Uma nova contratação parte de 7×7×5. Os tamanhos disponíveis passam a 7, 9, 11, 13 e 15 blocos, com cinco camadas de Y+1 a Y+5 e ampliação por seleção manual.

As abelhas vinculadas usam os limites atuais, com margem para o volume da entidade. O movimento normal de voo é limitado na borda e a navegação procura uma posição interna desobstruída. Uma abelha deslocada para fora pode desviar de obstáculos em seu retorno. Portais, pistões, comandos e a saída vanilla de uma colmeia à beira da área podem produzir uma posição exterior temporária.

A atração começa até dez blocos do trabalhador e mantém o destino no apiário. Um objetivo curto de voo orienta a abelha enquanto o trabalhador segura a flor. A alimentação ocorre dentro da área, com proximidade e linha de visão. A coleta pronta mantém prioridade.

A reprodução seleciona uma dupla adulta próxima, consome duas flores e abre uma reserva persistente para um filhote. O evento de nascimento valida a dupla e a capacidade. A cria recebe o vínculo ao entrar no mundo. Crescimento, intervalos e colmeias seguem o Minecraft.

## Evidência

Os GameTests verificam a prioridade da mão, o vínculo visual, a proteção após saída da colmeia, o alcance de dez blocos, as seis faces territoriais, a migração, a reprodução vanilla e a preservação de inventário. A suíte gráfica verifica as opções da interface. Outra JVM reabre o mesmo mundo e verifica a persistência.
