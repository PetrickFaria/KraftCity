# KraftCity 1.1.11

Base `v1.1.10`, commit `ee9077db5f0886b57f9414e89f7532c3f9ff1d2d`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Área e irrigação

O balde de água hidrata a terra arada em toda a área horizontal selecionada, de 5×5 a 13×13, incluindo os cantos e as alturas em que o posto trabalha. O índice de irrigação acompanha o redimensionamento assim que o posto grava a seleção. A umidade é aplicada pelo ciclo normal da terra arada. O efeito depende de um posto ativo com trabalhador e balde no slot 30. Remover o balde devolve a terra às regras vanilla. O efeito é virtual, conserva o balde e exige água real para plantar cana-de-açúcar.

## Vinculação e acesso

Abra a interface do Fazendeiro e clique em **Vincular Baú**. A interface fecha e dá 30 segundos para clicar com o botão direito no recipiente. O alcance é de cinco blocos medidos da posição válida mais próxima da área de trabalho. O servidor informa se o alvo estiver fora da área, bloqueado ou sem inventário acessível. Para remover a associação, clique em **Vinculado** e escolha **Desvincular** ou **Cancelar**.

A seleção registra coordenadas e identidade persistente do recipiente. Se o bloco for substituído, o posto invalida o vínculo. Um recipiente temporariamente descarregado preserva a associação. Enquanto vinculado, o Fazendeiro oferece os excedentes somente àquele recipiente e conserva os itens recusados. Postos sem vínculo continuam com a descoberta automática. O Baú do Ender usa o inventário pessoal do jogador que autorizou aquela vinculação, enquanto estiver conectado. As autorizações antigas continuam legíveis.

O descarte aceita baú e barril vanilla, o Baú do Ender autorizado e manipuladores de inserção `IItemHandler` publicados pelo Forge. Recipientes que implementam `WorkerDiscardAccess` podem decidir o acesso com base no aldeão e na face utilizada. Os testes cobrem filtros, inserção parcial, depósitos em um mesmo estoque por terminais diferentes e persistência. Um estoque virtual que só existe atrás de uma interface de jogador requer API pública ou um adaptador que aplique as regras de acesso do provedor.

O mundo anexado menciona a entidade de bloco `bausarcanos:bau_arcano`. A cópia sem o mod levou o Minecraft a ignorar essa entidade. O anexo contém o mundo, sem o JAR ou o código do Baús Arcanos. Para verificar sua API, permissões e rede de armazenamento, é preciso o JAR da versão usada ou o código-fonte correspondente. A cobertura Forge e de armazenamento compartilhado usa recipientes de teste com contratos conhecidos.

## Navegação

Diante de colisão persistente, o Fazendeiro encerra mais cedo a rota travada e procura outra face acessível. A navegação nativa pode subir obstáculos transitáveis. As rotas de descarte descartam mudanças de altura que passem por terra arada. Ao encerrar a transferência, o posto libera a rota para a rotina residencial. A cena de regressão combina descarte físico, obstáculo ao lado do barril, saída espontânea e chegada à cama.

## Estoque e recuperação

A colocação gera um UUID interno para o posto. A quebra anterior copiava esse UUID para o item mesmo quando o posto era genérico e vazio. Um Bloco Industrial sem dados particulares agora volta como item simples na Sobrevivência e sua pilha chega a 64. No Criativo, a quebra desse bloco vazio remove o registro e segue sem drop. Postos com inventário, equipamento ou configuração preservam os dados e a recuperação correspondente. O item simples tem a mesma forma usada no Criativo e por comandos ou receitas externas que produzam o item padrão.

## Auditoria dos slots do Fazendeiro

Os quatro slots auxiliares usam o filtro padrão do menu, que aceita qualquer pilha, inclusive itens que o Fazendeiro não usa. A lógica de trabalho interpreta cada slot assim.

| Slot | Rótulo | Uso efetivo |
| --- | --- | --- |
| 27 | Enxada | Usa itens do tipo enxada para arar e colher. Reabastece a partir do inventário compartilhado quando vazio. |
| 28 | Farinha de Osso | Usa farinha de osso ao adubar culturas elegíveis. Reabastece a partir do inventário compartilhado quando vazio. |
| 29 | Insumo | Slot reservado. Aceita itens como sementes e fertilizante, mas nenhuma ação agrícola os lê dali. |
| 30 | Balde de Água | Reconhece exclusivamente o balde cheio de água para a irrigação virtual. Reabastece a partir do inventário compartilhado quando vazio. |

Para plantio ou replantio, mantenha sementes e outros itens plantáveis no inventário compartilhado de 27 posições. Um item incompatível em um slot auxiliar ocupado impede seu reabastecimento automático.

## Rendimento agrícola

O produto principal usa um sorteio inteiro uniforme por colheita. A pedra agora usa seu próprio nível, e as faixas de ferro até netherita foram ajustadas à tabela solicitada.

| Enxada | Quantidade possível | Probabilidade por quantidade |
| --- | --- | --- |
| Madeira | 1 | 100% |
| Pedra | 1–2 | 1/2 |
| Ferro | 1–3 | 1/3 |
| Ouro | 1–4 | 1/4 |
| Esmeralda | 1–5 | 1/5 |
| Diamante | 1–6 | 1/6 |
| Netherita | 1–7 | 1/7 |

A seleção do bloco, o veto do Forge, o desgaste e os subprodutos continuam no fluxo da colheita individual. O teste de conservação verifica as entregas das entidades físicas e a reserva para replantio. A cana mantém a base, inclusive no cenário com alturas 2/3/2 que termina em 2/1/2. Melancias e abóboras mantêm suas regras específicas e os caules compatíveis.

Uma rodada adicional em um ambiente com Quark 3.4-418 e AutoRegLib 1.8.2-55 aprovou 85 GameTests, incluindo o veto do Forge, as seis colheitas seletivas com navegação e os dois cenários de cana adjacente.
