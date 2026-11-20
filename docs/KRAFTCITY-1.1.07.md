# KraftCity 1.1.07

Base 1.1.06, commit `9f6735e65ad1965235d123186f0751247c8d6f5b` de `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

- O progresso de uma rota é medido pela distância restante sobre os nós da navegação terrestre. Oscilar diante de um obstáculo já não conta como avanço. Os destinos coincidem com a célula de apoio escolhida e as tentativas de descarte verificam o espaço de interação ao redor do recipiente. A reprodução gráfica revelou que um item repousando abaixo do topo inteiro da terra arada pode fornecer uma coordenada dentro do próprio solo. A coleta agora busca a célula de caminhada sobre a superfície e mantém a exigência de alcance físico.
- A recuperação de água rasa procura uma margem seca alcançável. A navegação e o controle de salto vanilla movem a entidade, inclusive quando a rotina residencial assume ao anoitecer.
- A colheita de frutos atualiza o fruto e os caules que apontavam para ele. O restante da lavoura fica fora das atualizações de vizinhança disparadas por essa colheita. A proteção contra pisoteamento continua restrita ao solo administrado.
- Uma falha de navegação temporária conserva a reserva residencial válida enquanto o trabalhador procura uma cama acessível. A aproximação pode usar o espaço ao lado da cama ou o próprio colchão alcançável. A rotina mantém as verificações do POI real.
- Um período de trabalho observado libera a verificação do próximo encerramento, inclusive após comandos que reutilizam o mesmo horário absoluto. Os prazos e as tentativas pendentes continuam no posto. O descarte simula a inserção na face que será usada e preserva o excedente recusado.
- À noite, os trabalhadores perseguidos testam uma rota que evite o zumbi e conduza à residência vinculada. Um desvio inicial dá espaço para reagir quando a rota está fechada. O retorno usa a navegação física e as portas permitidas da residência.
