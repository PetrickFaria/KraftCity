# KraftCity 1.1.16

Base validada na 1.1.15, commit `d86754bb0f575a3c6558ba7342e63c69a2f1a6ab`, branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Equipamentos e armazenamento

O menu apresenta seis posições de equipamento e uma linha de nove posições de armazenamento. O pagamento continua em uma posição própria. Os itens de versões anteriores além da nona posição continuam no posto e podem ser retirados pela tela de recuperação, inclusive quando os nove slots novos já estão ocupados.

O clique direito com o cursor vazio sobre equipamento no inventário do jogador transfere uma unidade para o tipo correto, se o destino estiver vazio. O servidor aplica a transferência ao mesmo inventário do posto aberto pelo bloco ou pelo Soldado. O clique manual permanece disponível. Dados de durabilidade, encantamentos e outros dados do item seguem com a pilha transferida.

A coleta verifica distância, linha de visão, espaço e uma rota acessível. Em Patrulhar, o Soldado pode percorrer essa rota em momentos sem combate. Em Seguir e Guarnecer, só recolhe itens ao alcance físico quando a responsabilidade principal está satisfeita. A capacidade parcial deixa o restante no chão.

## Deslocamento, clima e proteção

A área inclusiva de 201×21×201 vem das coordenadas do posto. Destinos e nós de navegação são validados dentro desse volume. A movimentação do Soldado em serviço respeita a fronteira e os projéteis militares passam pela validação de impacto. A busca é local, usa entidades carregadas e reavalia ameaças periodicamente. A caminhada usa 0,5 e a corrida de combate usa 0,75 como fatores de navegação, correspondentes aos fatores de rotina e fuga do aldeão vanilla.

Seguir acompanha o jogador até sete blocos quando existe uma rota possível, dá prioridade aos ataques contra o jogador e ao perigo que se aproxima dele e permite autodefesa. Patrulhar procura destinos a pé, protege moradores e trabalhadores e considera a distância até os limites dos locais de trabalho e das residências. Guarnecer permanece centralizado sobre o posto, virado conforme o bloco, e retorna após o combate. Para alcançar um alvo corpo a corpo, o Soldado inicia a descida pelo lado do alvo quando encontra espaço livre e apoio firme, evitando uma rota que atravesse o bloco do posto. Se já estiver no chão do lado oposto ao alvo, busca uma posição lateral para contornar o posto. A mudança de modo encerra a atividade anterior e limpa sua rota.

Hostis naturais são elegíveis e a Blacklist adiciona outros tipos. Nos modos de defesa local, ataques diretos contra residentes e trabalhadores têm prioridade sobre autodefesa, depois vêm as aproximações a pessoas, áreas e ao Soldado. Entidades aliadas e jogadores são protegidos. Chuva e trovões mantêm as tarefas militares em serviço. Os turnos e o descanso seguem as regras anteriores.

## Combate, animações e persistência

Espadas usam o ataque físico e a durabilidade do equipamento. Arcos e bestas mantêm munição finita e os tempos de preparo. A exceção da Infinitude segue a regra existente para flechas comuns. Escudos compatíveis usam a mão secundária e recebem desgaste. A mão direita acompanha o balanço do ataque e os braços acompanham o uso do escudo, do arco ou da besta nos modelos de Aldeão e Steve. Na mira, o tronco gira em direção ao alvo e a pose de duas mãos mantém o escudo equipado sem mostrá-lo no caminho da arma.

A estação continua como fonte única dos equipamentos e do armazenamento. O salvamento existente mantém modos, jogador vinculado, Blacklist, XP e identidade. A transferência e a representação gráfica não criam um segundo inventário. As rotinas de zumbificação e cura seguem a identidade existente do Soldado.

## Testes

Os testes militares cobrem slots, clique direito e NBT, coleta parcial, fronteiras por eixo, defesa de residentes, autodefesa e combate com munição. O cenário com IA ativa verifica coleta, retorno à guarda, combate sob trovões e acompanhamento do jogador. O cliente testa a transferência com o clique de inventário do jogo. A suíte geral inclui as regressões de colheita e a reabertura do mesmo mundo em outra JVM. Os testes de autorização anteriores passaram a verificar a regra de Blacklist aditiva solicitada.
