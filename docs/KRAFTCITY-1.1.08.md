# KraftCity 1.1.08

Base 1.1.07, commit `f64fdd240853ca6fbab992ad84b3cf6a00a11aa1` da branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Colheita

O replantio usava uma escrita temporária no mundo para montar o evento Forge de colocação. A restauração revertia somente a célula selecionada. Um evento que reagisse ao estado temporário podia modificar um vizinho mesmo se a colheita fosse cancelada. O cenário de reprodução usa um observador Forge que reage a esse estado e registra o cultivo selecionado, o estado visível no mundo, a proposta, a posição física do Fazendeiro e o vizinho. A autorização agora apresenta a proposta nos campos do evento e deixa o mundo intacto até a confirmação. Os testes de caminhada comparam os cultivos e solos para trigo, cenoura, batata, beterraba, melancia e abóbora. A exceção de pisoteamento continua vinculada ao solo vazio selecionado junto ao caule.

## Armazenamento

O caminho vanilla é reservado aos blocos vanilla correspondentes. Recipientes de mods usam o `ITEM_HANDLER` da face exposta e podem delegar a um armazenamento compartilhado. A interface opcional `WorkerDiscardAccess` também permite que um BlockEntity forneça acesso mediante a identidade real do aldeão e a face. A autorização do mod, as permissões Forge e os filtros continuam valendo. O Fazendeiro verifica a inserção simulada e debita apenas o que o destino aceita de fato. O teste inclui dois pontos de acesso para o mesmo canal, uma face autorizada, um ponto negado, filtro de item e espaço para apenas duas unidades. O Baú do Ender vanilla e inventários privados sem API de automação ficam fora desse acesso.

## Movimentação

A chegada imediata à face de um recipiente Forge podia seguir para a transferência sem a limpeza feita pela chegada de uma rota normal. O encerramento havia parado o caminho sem zerar o destino do controlador de movimento. A transferência agora encerra o comando de caminhada e as memórias associadas antes de devolver o controle. A rotina diurna tem duração limitada e libera o caminho quando acaba ou quando o destino deixa de aceitar os itens. O descarte noturno continua integrado à residência. Os testes verificam caminhada real até o recipiente, saldo parcial do canal vinculado, retorno à área para colher e fuga espontânea depois do descarte com a saúde intacta.

## Reprodução e cobertura

- A inspeção separou a alteração do alvo na colheita, as alterações de suporte pelo pisoteamento e a propagação por Forge. Na reprodução com a 1.1.07, o observador encontrou trigo de idade 0 no mundo durante o evento e removeu a cenoura adjacente antes de cancelar a colocação. Com a correção, o observador encontrou trigo de idade 7 no mundo e idade 0 na proposta. O trigo maduro, a cenoura e a terra permaneceram no lugar após o cancelamento. A reprodução identifica o efeito da escrita antecipada usando um observador controlado.
- Os cenários com seis cultivos registram a posição de chegada, o estado anterior e posterior do alvo e os estados vizinhos. Eles também observam a ida do aldeão até um item solto e repetem a comparação depois da coleta. Na colheita de melancia e abóbora, a remoção do fruto faz o caule voltar ao estado maduro sem fruto.
- O ensaio de armazenamento vinculado compara a contagem do inventário profissional com o saldo no canal compartilhado. Dois pontos autorizados veem o mesmo saldo de 64 cenouras, partindo de 62. A origem conserva 13 das 15 cenouras e os dois gravetos rejeitados. O teste também cobre acesso negado e destino descarregado, além do retorno físico até a cultura madura. As integrações são validadas com recipientes de teste do Forge e da API pública do KraftCity.
- O ensaio de fuga introduz o zumbi depois do descarte e verifica deslocamento espontâneo com a saúde do aldeão intacta. No cliente gráfico, três Fazendeiros encerram o expediente com descartes em inventários separados e chegam a camas distintas. A segunda JVM reabre o mesmo mundo salvo e verifica os três dormindo novamente.
