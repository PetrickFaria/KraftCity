# KraftCity 1.1.10

Base validada `v1.1.09`, commit `c4ee8009449734057ec101cef99b86a5ffc1e91e`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Estoque, sons e reprodução

Na 1.1.09, os produtos saíam como entidades físicas sem identificação de origem. O controle de coleta aplicado ao Fazendeiro deixava aberta a coleta vanilla de outros aldeões. Além disso, o inventário pessoal e a reprodução vanilla tinham regras separadas do estoque profissional. Uma pilha consumida por essa rota nunca entrava no inventário profissional.

Os drops agrícolas agora carregam o identificador do posto e só se mesclam com pilhas da mesma origem. Aldeões comuns continuam coletando alimentos comuns. A coleta de drops profissionais pertence ao posto ativo. O servidor simula o espaço, transfere a parte aceita e reduz a entidade somente depois da inserção. Aldeões contratados não participam do acasalamento vanilla e o evento de nascimento do Forge também é vetado quando um dos pais é um trabalhador contratado.

Uma colheita confirmada toca o som de quebra do bloco correspondente. Na cana, os dois segmentos superiores geram um som para a operação, com a base preservada. A rotina conserva o veto do Forge introduzido na 1.1.09. A enxada real continua determinando o rendimento e o desgaste sem aparecer na mão do ator sintético durante a consulta.

## Área e navegação

O nível libera as opções de 5×5 a 13×13. A seleção ativa é gravada no posto e só muda por escolha válida. A atualização conserva o tamanho salvo e usa o tamanho anterior do zigue-zague para migrar postos sem seleção explícita. A tela exibe as opções travadas e o contorno acompanha a seleção sincronizada. O posto recuperado de um Fazendeiro leva somente a configuração de tamanho quando ela difere do padrão. Os itens do inventário continuam caindo separadamente conforme a regra da 1.1.09.

A busca de itens foi reduzida para um bloco além de cada limite horizontal e ampliada até um bloco abaixo da base. Plantio e colheita continuam usando a área agrícola original. O item dentro de um campo tem prioridade sobre uma extensão vizinha. Nas sobreposições, o posto de origem é usado quando disponível. Para drops sem origem, distância e identificador desempataram a seleção. As rotas de coleta evitam os campos exclusivos dos vizinhos e a transferência roda no servidor.

## Descarte e recuperação

O encerramento mantém o destino enquanto existe uma aproximação válida. Uma tentativa concluída, inclusive parcial, sai da fila daquele expediente. O Fazendeiro pode procurar outro recipiente válido e libera a navegação quando termina, abandona uma rota bloqueada ou esgota a busca. O encerramento limpa o caminho, as memórias de movimento e os bloqueios temporários antes da rotina residencial. No cliente de teste, o agendador residencial também conseguiu atribuir uma entrega de fragmentos durante o descarte, tomando o controle da rota e impedindo a ida à cama. À noite, o Fazendeiro não recebe uma nova entrega. Se já estiver transportando fragmentos, o sistema residencial devolve a carga pela rotina existente antes de iniciar o encerramento agrícola.

O Baú do Ender vanilla continua exigindo a autorização explícita do proprietário. Agache-se e clique com um Olho do Ender na mão principal para autorizar ou revogar. O proprietário precisa estar conectado. Dois baús autorizados do mesmo proprietário veem o mesmo inventário e a inserção parcial debita somente o que entrou. Recipientes de mods continuam utilizando os contratos de automação que oferecem via Forge ou KraftCity.

Os blocos Industrial, Comercial, Militar e Construtor genéricos e sem dados empilham até 64. Blocos especializados e itens com dados de recuperação permanecem individuais. A colocação de um registro de recuperação continua aceitando a identidade uma única vez.
