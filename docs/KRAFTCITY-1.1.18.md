# KraftCity 1.1.18

Base 1.1.17, commit `4cebb7c3e1962544dc00105f4e95f291b81fe066`, branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Interface e contratação

O menu usa um slot por categoria de equipamento e nove slots de armazenamento. A validação manual usava o índice global herdado do Slot, fora da faixa de índices locais de equipamento. Com o índice correto, a inserção manual valida o tipo do item e transfere uma unidade ao registro persistente. A substituição manual mantém o equipamento anterior no cursor. Shift + clique tenta somente o destino específico para equipamentos; se estiver ocupado, a pilha fica com o jogador. Os itens mantêm suas tags, encantamentos e desgaste. O clique direito anterior permanece disponível.

O pagamento fica na tela de contratação. Ao escolher o morador, o cliente informa sua seleção e o servidor calcula o valor real, inclusive o desconto de recontratação. A confirmação consome o preço e devolve eventual saldo antigo ao jogador, desde que exista espaço. O saldo legado dos Soldados contratados pode ser retirado na tela de recuperação, com o índice persistente original.

Seguir, Patrulhar e Guarnecer ocupam a primeira linha. Os dois turnos e a Blacklist ocupam a segunda. A visualização Limites usa linhas vermelhas e as dimensões do próprio servidor, 201 × 31 × 201. A visualização é local por posto e some ao desativar ou trocar de mundo. O cabeçalho usa as dimensões e o fundo da barra do Fazendeiro e recolore apenas o preenchimento de XP, proporcional ao nível militar.

## Alvos e combate

Estações sem configuração explícita permitem hostis vanilla por padrão, com Creeper desmarcado. Aranhas mantêm sua condição de hostilidade. Tipos neutros como Enderman e alvos de mods podem ser permitidos explicitamente. Listas salvas, inclusive listas vazias, conservam a escolha anterior. A Blacklist autoriza a seleção, a perseguição e o dano, junto com as restrições de fogo amigo e da área do posto.

As duas formas de aldeão usam os movimentos de carregamento e mira da besta vanilla e a rotação progressiva do arco. O combate preserva os tempos, munição, encantamentos e durabilidade. Fora da mira, as poses normais continuam em uso.

## Descarte

O botão Vincular baú reaproveita a vinculação do Fazendeiro e indica as coordenadas do recipiente escolhido. O alcance militar é de cinco blocos em cada eixo a partir do posto. O Soldado só inicia a tarefa depois de verificar ameaças. Ele procura acesso físico, respeita o alcance do modo Seguir e retoma o modo após transferir os itens que cabem. As armas e armaduras equipadas ficam no posto. Flechas disponíveis ficam reservadas quando a arma equipada usa munição. O vínculo persiste com o recipiente e mantém as verificações de acesso de baús privados.

## Vida

Residentes e trabalhadores têm capacidade base de 14 HP. A capacidade militar usa 14, 18, 22, 26 e 30 HP, dos níveis 1 a 5. Subir de nível mantém o HP atual. Sair da função militar como residente restaura o teto de 14 HP. A mudança reutiliza o atributo base, sem adicionar modificadores, e preserva a regeneração residencial de 1 HP por 600 ticks dormidos.
