# KraftCity 1.1.31 — capacidade e estoque do apiário

Base 1.1.30, commit `c4585f80262274c01df5e67408cb43c1f449eb47`. Minecraft 1.19.2, Forge 43.5.0 e Java 17.

## Capacidade e redução

A capacidade efetiva é o menor valor entre nível e faixa da largura selecionada. As larguras 7, 9, 11, 13 e 15 autorizam de uma a cinco colmeias. Cada colmeia autorizada abre três vagas de domesticação. A redução bloqueia novas domesticações e reproduções acima do limite e mantém os vínculos existentes.

A lista ativa usa somente colmeias na área atual, em ordem de distância ao posto, com desempate estável. A expansão reconhece primeiro as colmeias que já existem no mundo. A associação de colmeia é registrada junto ao identificador individual da abelha. Os estados legados sem colmeia identificada são atualizados quando a abelha é observada no mundo ou entra em uma colmeia. O posto deixa de administrar colmeias excluídas pela nova seleção. Elas e seus ocupantes permanecem no mundo.

## Recolhimento e recolocação

As abelhas domésticas só procuram colmeias ativas quando precisam de uma nova morada. As abelhas que já usavam uma colmeia excedente podem voltar a ela. O Apicultor compara os identificadores de quem voltou com os vínculos de quem usa aquela colmeia e também procura abelhas associadas ainda do lado de fora. Os ocupantes domésticos ficam abrigados durante o recolhimento com espaço no estoque, com as saídas de emergência preservadas. O trabalhador continua outras atividades enquanto aguarda.

O recolhimento requer uma vaga no inventário e respeita os eventos Forge. O item guarda o tipo de colmeia, o nível de mel e os dados do bloco, incluindo os ocupantes e seus identificadores. A recolocação requer capacidade livre e uma cerca isolada com saída desobstruída dentro da área. Itens recolhidos em versões anteriores continuam elegíveis. A capacidade e o inventário são conferidos novamente em cada ação. Uma colmeia guardada sempre tem prioridade sobre a fabricação.

## Fabricação e reserva

A fabricação usa seis favos e três tábuas, nas quantidades solicitadas para o KraftCity. A receita vanilla do Minecraft 1.19.2 usa a proporção inversa. O Apicultor aceita combinações de tábuas da tag de itens vanilla. O plano de consumo e a saída são verificados em uma cópia do inventário. Uma fabricação aprovada grava o consumo e o resultado na mesma operação do servidor. Ela pode usar os seis favos reservados.

O descarte considera o total de favos em todas as pilhas e protege os primeiros seis. Favos de mel excedentes permanecem elegíveis ao descarte, assim como antes para os demais produtos. A reserva é formada pelos favos reais e se recompõe quando a tesoura fornece novos favos. Os frascos mantêm a prioridade na coleta de mel.
