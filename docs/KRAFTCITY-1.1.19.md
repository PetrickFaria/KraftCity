# KraftCity 1.1.19

Base validada na 1.1.18, commit `3ee7830a9e728838b6dfcaf4fa2075d458875a3c`, na branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Fuga e notificações

A detecção profissional filtrava qualquer entidade que conservasse o vínculo militar. O filtro incluía o Soldado Zumbi, embora ele já tivesse IA hostil. A categoria de ameaça agora distingue a forma zumbificada do registro profissional. Residentes sem emprego também detectam essa forma. As rotas usam a navegação e os limites residenciais ou profissionais aplicáveis. A IA de defesa dos Soldados saudáveis permanece sob as regras militares.

As mensagens de conversão e cura são enviadas depois da entrada da entidade transformada ou curada no mundo. O evento de morte militar usa prioridade baixa após a conversão, que cancela a morte quando bem-sucedida. A morte definitiva envia uma única mensagem militar e conclui a remoção residencial. O jogador associado ao posto e os jogadores a até 64 blocos no mesmo mundo recebem os avisos. Recarregar as entidades não envia eventos novamente.

## Interface

O menu usa uma área de 246 × 225 unidades de GUI. A página principal contém equipamentos, os nove slots de armazenamento, o inventário do jogador, XP e os controles de modo e turno. O botão de seta abre Configurações, que contém Blacklist, Limites, vinculação e confirmação de desvinculação do baú e demissão. As páginas usam o mesmo menu e conservam seus slots e estado. A grade foi verificada no cliente com escalas 1, 2, 3 e Auto para uma janela de 960 × 720, incluindo área útil de 320 × 240.

## Armaduras

A camada de armadura reutiliza o renderizador humanoide do Forge, suas texturas, tons de couro, brilho de encantamento e suporte a modelos registrados. No aldeão nativo, o tronco acompanha a pose do modelo, as pernas acompanham as pernas do aldeão e os braços seguem o mesmo cálculo das animações profissionais. O tronco da armadura tem profundidade ajustada para a roupa. A calça recebe um ajuste separado para aparecer de frente sobre a túnica quando o Soldado está parado. No modelo Steve, a camada copia a pose do modelo existente e renderiza inclusive o capacete. Para o aldeão nativo, o casco vanilla de capacete não acompanha a cabeça mais alta e a projeção do nariz. A camada omite esse casco e preserva o slot, a proteção e o desgaste.

## Validação

O GameTest de fuga verifica deslocamento real de um residente, um Fazendeiro e um trabalhador comercial, além do vínculo após a transformação e cura. A suíte gráfica usa os slots reais, alterna as páginas, verifica o espaço da GUI e recebe no cliente os avisos de transformação e cura. O cliente confirma avisos únicos para morte definitiva por queda e por explosão de Creeper. O cenário militar também verifica o vínculo do baú, o estoque, a progressão e as regressões de combate ao reabrir o mesmo mundo.
