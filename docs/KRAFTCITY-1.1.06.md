# KraftCity 1.1.06

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base 1.1.05, commit `230cdb77181249979d6c1eb98b11073136ca469e` na branch `feature/kraftcity-builder`.

## Sono e descarte

- A rotina residencial assume a navegação noturna do trabalhador no endereço vinculado. O alvo de cama permanece estável enquanto a rota avança. O trabalhador usa a cama real registrada como POI, conserva a reserva e libera a navegação ao amanhecer. A patrulha dos soldados cede à residência fora do turno noturno configurado.
- O Fazendeiro encerra o expediente mesmo com `/time set night`. A coleta de fechamento fica restrita à área e tem prazo próprio. O descarte reserva dez unidades por item plantável, respeita inventários Forge e conserva o estoque que o recipiente não pode receber.
- O teste de horário revelou que a navegação terrestre rejeita o primeiro pedido de caminho quando a entidade ainda está sem apoio. Uma espera curta pelo contato com o chão agora precede a rejeição do destino. Rotas sem deslocamento tentam outro acesso e têm prazo e intervalo de espera. Uma troca de rotina ou fuga limpa o estado de navegação transitório e conserva a sequência da área. Quando o campo fica além do alcance de uma rota, o retorno usa destinos intermediários mais próximos do posto.

## Agricultura e ocupação

- A colheita registra o alvo, a posição física do trabalhador e os estados antes e depois. Os cenários 5×5 com trigo, cenoura, batata e beterraba registram também as 24 células vizinhas e os 25 solos. Melancia e abóbora continuam liberando o caule sem removê-lo.
- O pisoteamento é uma alteração física do solo, seguida pela atualização da cultura acima. No cenário de controle, a queda física de um aldeão converteu a terra arada em terra comum e removeu a cenoura acima. A mesma queda dentro da área ocupada preservou o solo e a cultura. Durante o salto intencional, a conversão do solo selecionado respeita o evento de pisoteamento do Forge. A rotina pode usar um espaço lateral já pronto e protege os demais solos da lavoura.
- A cana usa as regras de sobrevivência vanilla e exige água real dentro da área para o plantio automatizado. Depois da etapa de cana, as varreduras normais começam pelo noroeste. Os cursores ficam no posto e a cana de três blocos preserva a base e produz itens coletados no mundo.
- Visitantes ociosos procuram um destino seguro fora de áreas ocupadas. Os limites de um posto próprio continuam válidos em regiões sobrepostas e as rotas de trabalho cooperativo, abastecimento e fuga conservam prioridade.

## Verificação em jogo

- Os cenários de sono e descarte executam o agendamento do servidor, a caminhada, a transferência e a cama real. Os cenários de ameaça colocam os próprios zumbis no mundo de testes.
- O cliente gráfico percorre o campo, confere a saída do visitante, colhe após desobstruir a rota e observa o salto, o descarte e o sono. A segunda JVM confere os dois cursores da área e a reserva da cama no mesmo mundo.
