# KraftCity 1.1.22

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base publicada 1.1.21, commit `4ddafa06901d69922d64ec700e96d88a726f0748`, branch `feature/kraftcity-builder`.

## Armadura no modelo renderizado

No Fresh Animations 1.8, os arquivos de aldeão mantêm a peça `head` com rotação horizontal e vertical zerada. A cabeça visível é desenhada por uma peça CEM de face associada a `nose`, com deslocamentos próprios. O caminho da 1.1.21 copiava o estado de `head` na camada de armadura. O capacete agora usa a cadeia de transformações da geometria real quando o modelo carregado oferece a estrutura correspondente. O ponto de referência vem das dimensões da cabeça, e o encaixe local de 2,8 unidades da versão anterior continua sendo aplicado.

A posição vertical de calças e botas usa o pivô da perna e uma pequena folga derivada da expansão da malha de armadura. Os pivôs CEM do Fresh Animations podem estar na altura dos pés e delegar a geometria da perna a `left_leg2` ou `right_leg2`. Quando essa geometria está presente, o alinhamento vertical usa a origem efetiva da perna a partir da matriz da cena. Texturas, proteção, brilho de encantamentos e slots continuam pelo renderizador de armadura do Forge. Steve e o peitoral conservam seus próprios ajustes.

A integração encontra as peças em tempo de execução e usa o caminho padrão para modelos sem essa estrutura. O pacote Fresh Animations e os carregadores CEM são opcionais. A comparação de poses usou o ZIP Fresh Animations 1.8 fornecido, Entity Model Features 2.0.2 e Entity Texture Features 6.0.1 no Forge 43.5.0. OptiFine e outras combinações de carregadores CEM ainda exigem validação no ambiente de jogo que os utiliza.

## Intervalo efetivo do arco

Na 1.1.21, a rotina profissional já esperava 100 ticks, mas a entrada de projéteis do Soldado no mundo apenas os identificava. Uma segunda origem que inserisse outra flecha diretamente não passava pelo temporizador. O teste de regressão reproduz essa via de entrada. A origem exata dos disparos rápidos observados no mundo do usuário permanece sem diagnóstico direto.

O Forge faz a verificação na entrada da flecha no servidor. O primeiro projétil aceito reserva 100 ticks para aquele Soldado e registra o tick no projétil. Tentativas antecipadas são canceladas, inclusive no mesmo tick. A identidade e o prazo são salvos junto com o Soldado e espelhados no posto para continuidade de dados. Flechas reconhecidas como disparos de besta têm sua cadência própria. Projéteis que já existiam no arquivo do mundo continuam carregando normalmente.

## Detecção e recuo

Patrulhar classifica ameaças territoriais a partir dos residentes, das áreas protegidas e das entidades carregadas. A 1.1.21 já condicionava parte da verificação à rota viável no instante da descoberta. A classificação territorial agora ocorre sem pedir linha de visão ou caminho ao alvo. Depois da aquisição, o navegador tenta uma aproximação permitida. Alcance, visibilidade, fogo amigo e fronteira continuam validados na execução dos ataques.

O navegador vanilla chegava a girar o corpo em direção ao ponto de recuo e a revisão de orientação do corpo podia repetir esse giro. O controle de combate conserva o movimento que a rota determinou e converte seus componentes em avanço, marcha a ré ou deslocamento lateral em relação à mira. A cabeça acompanha a ameaça e o corpo mantém a orientação de combate. Quando há vários alvos válidos próximos, arqueiros e besteiros priorizam a distância com uma pequena margem para evitar alternância quando os inimigos estão quase equidistantes.

## Testes

Os testes militares registram os ticks dos projéteis no servidor e verificam tentativas antecipadas, independência entre Soldados, troca de alvo, cadência da besta e a continuidade após salvar. Também verificam aquisição territorial com a visão bloqueada, ausência de disparo através de uma parede e entradas de movimento de ré mantendo o alvo.
