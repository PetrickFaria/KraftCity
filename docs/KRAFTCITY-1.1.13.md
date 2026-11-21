# KraftCity 1.1.13

Base validada `v1.1.12`, commit `0cd849cf6d2cc014fb800056a3bc8fbd5a539a9c`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Barra de experiência

O menu profissional volta a desenhar o preenchimento com o recorte original de 182×5 da folha de ícones, na mesma posição e com o mesmo cálculo percentual. Uma folha de ícones de uso exclusivamente profissional recolore os pixels verdes do preenchimento em ciano, com intensidade proporcional e transparência original. Moldura, fundo, sombreamento, geometria, texto e barra do jogador conservam seu fluxo original. A comparação do cliente verifica pixel a pixel a área permitida, o alfa e a intensidade de cada tom modificado.

## Ferramentas e braços

O renderizador da mão usa a transformação de terceira pessoa do item. Foram retiradas as duas ampliações locais de 2× da enxada. Os braços continuam usando as mesmas curvas de caminhada e de trabalho agrícola. Para o modelo aldeão, o renderizador monta o recorte dos braços a partir da roupa e da pele da textura usada naquele morador. As mangas usam o casaco da skin ou a roupa de bioma aplicada ao tronco, e as mãos usam amostras opacas da pele da cabeça. As camadas e o modelo Steve continuam usando a própria skin.

Durante o descanso, o servidor informa ao cliente qual ferramenta profissional deve ser exibida. A ferramenta renderizada acompanha a parte de trás do tronco e é ocultada durante o sono. O renderizador deixa de exibir a ferramenta na frente enquanto ela está nas costas. O inventário e a durabilidade continuam sob controle dos slots profissionais. Combate e fuga podem interromper a pose visual de descanso.

## Retorno noturno

O aldeão usa a residência vinculada na mesma dimensão. O Fazendeiro encerra o descarte antes de entregar a navegação à rotina residencial. As buscas físicas longas preservam o alcance de 100 blocos. A rotina conta avanço quando o trabalhador percorre pelo menos dois blocos até um ponto novo. Se parar de avançar, ela tenta recalcular a rota. O prazo residencial normal de 300 ticks sem progresso permite recuperar o trabalhador em um ponto seguro da casa, por meio do mesmo teleporte de emergência usado para os moradores comuns. O prazo de ameaça preexistente continua aplicável às situações em que o controle residencial permanece ativo.

A recuperação exige trabalhador vivo, vínculo válido e destino livre em chunk carregado. A recuperação não carrega chunks nem altera profissão, identidade, progresso, estoque ou cama. A chegada solta o caminho de retorno e a procura da cama usa a rotina residencial aprovada.

## Regeneração enquanto dorme

O servidor concede 1 HP por 600 ticks ativos de sono a um morador ferido e vivo quando a residência vinculada e as duas metades da cama continuam válidas. Vale para residentes sem profissão e para trabalhadores com vínculo residencial. O limite de vida é o da entidade. Durante o sono residencial válido, o Soldado usa esse intervalo e suspende a cura militar para evitar dois efeitos simultâneos. Acordar, perder a cama ou descarregar a entidade reinicia a contagem parcial. O contador fica em memória, sem contabilizar tempo offline ou transportar progresso parcial para outra JVM.
